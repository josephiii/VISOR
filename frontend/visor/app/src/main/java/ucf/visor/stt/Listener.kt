package ucf.visor.stt

import android.content.res.AssetManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlin.concurrent.thread

class Listener(
    private val assets: AssetManager,
    private val onWake: (String) -> Unit,
) {

    private var spotter: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null

    @Volatile
    private var isRunning = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sampleRate = 16000

    // Build STT engine with sherpa-onnx
    init {
        val config = KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = sampleRate, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "kws/encoder.onnx",
                    decoder = "kws/decoder.onnx",
                    joiner = "kws/joiner.onnx",
                ),
                tokens = "kws/tokens.txt",
                numThreads = 1,
                provider = "cpu",
            ),
            // custom phrases go here
            keywordsFile = "kws/keywords.txt",
        )
        spotter = KeywordSpotter(assetManager = assets, config = config)
        Log.d("VISOR", "Listener ready")
    }

    // starts listening, should be called when session starts
    fun start() {
        val s = spotter ?: run {
            Log.e("VISOR", "No spotter; cannot start")
            return
        }
        if (isRunning) return
        isRunning = true

        val st = s.createStream()
        stream = st

        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        // FIXME?
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2,
        )
        audioRecord = record
        record.startRecording()

        // The thread works on its own references to the recorder, spotter and
        // stream: stop() may null the fields at any moment, and it waits for
        // this loop to finish before the native objects are released.
        worker = thread(name = "visor-kws") {
            val buffer = ShortArray(minBuf)
            while (isRunning) {
                val n = record.read(buffer, 0, buffer.size)
                if (n > 0 && isRunning) {
                    val samples = FloatArray(n) { buffer[it] / 32768.0f }
                    feed(s, st, samples)
                }
            }
        }
    }

    private fun feed(s: KeywordSpotter, st: OnlineStream, samples: FloatArray) {
        st.acceptWaveform(samples, sampleRate)
        while (s.isReady(st)) {
            s.decode(st)
        }
        val keyword = s.getResult(st).keyword
        if (keyword.isNotBlank()) {
            s.reset(st)
            mainHandler.post { onWake(keyword) }
        }
    }

    /**
     * Stops listening. Waits for the audio thread to leave [feed] before the
     * native stream is released: releasing it underneath a decode in progress
     * crashed the app (SIGSEGV in sherpa-onnx), both on activity teardown and
     * whenever "VISOR GO" paused wake listening mid-buffer.
     */
    fun stop() {
        stopAndJoin()
    }

    fun shutdown() {
        // A thread still decoding is inside the spotter too; leave it be.
        if (!stopAndJoin()) spotter?.release()
        spotter = null
    }

    /** @return true when the audio thread was still running after the timeout. */
    private fun stopAndJoin(): Boolean {
        isRunning = false
        audioRecord?.stop() // unblocks a pending read()
        val stuck = worker?.let { it.join(JOIN_TIMEOUT_MS); it.isAlive } ?: false
        worker = null
        audioRecord?.release()
        audioRecord = null
        if (stuck) {
            // Still decoding after the timeout: leaking one stream is better
            // than freeing it under the thread that is using it.
            Log.w("VISOR", "KWS thread did not stop in time; not releasing its stream")
        } else {
            stream?.release()
        }
        stream = null
        return stuck
    }

    private companion object {
        const val JOIN_TIMEOUT_MS = 1_000L
    }
}