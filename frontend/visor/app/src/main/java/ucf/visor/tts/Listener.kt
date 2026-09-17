
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
    @Volatile private var isRunning = false
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

        stream = s.createStream()

        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2,
        )

        audioRecord?.startRecording()

        thread(name = "visor-kws") {
            val buffer = ShortArray(minBuf)
            while (isRunning) {
                val n = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (n > 0) {
                    val samples = FloatArray(n) { buffer[it] / 32768.0f }
                    feed(samples)
                }
            }
        }
    }

    private fun feed(samples: FloatArray) {
        val s = spotter ?: return
        val st = stream ?: return

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

    fun stop() {
        isRunning = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        stream?.release()
        stream = null
    }

    fun shutdown() {
        stop()
        spotter?.release()
        spotter = null
    }
}