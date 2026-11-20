package ucf.visor.lookaround

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Plays [GuideTone] while Look around leads the head somewhere: a beep, then a
 * gap, again and again, each from the latest error, so the sound follows the
 * head as it turns.
 *
 * The beeps are synthesized once, at [STEPS] pitches, as Chimes and the lab's
 * CuePlayer are, and played as media like VISOR's voice, so they come from the
 * glasses when the glasses are the phone's audio device.
 *
 * @param loudness the guidance tones' volume (Settings > Sound), read at each beep.
 */
class GuideTonePlayer(private val loudness: () -> Float) {

    private val tracks: List<AudioTrack?> = List(STEPS) { step ->
        val hz = GuideTone.LOW_HZ + (GuideTone.HIGH_HZ - GuideTone.LOW_HZ) * step / (STEPS - 1)
        runCatching { buildTrack(render(hz)) }
            .onFailure { Log.w(TAG, "Could not prepare the $hz Hz guidance beep", it) }
            .getOrNull()
    }

    private var loop: Job? = null

    /**
     * Beeps toward wherever [error] says (degrees to turn), until [stop]. A
     * null error is a pause: nothing to lead toward right now, as while a
     * picture is being taken.
     */
    fun start(scope: CoroutineScope, error: () -> Double?) {
        stop()
        loop = scope.launch {
            while (isActive) {
                val now = error()
                if (now == null) {
                    delay(IDLE_POLL_MS)
                    continue
                }
                play(GuideTone.pitchStep(now, STEPS))
                delay(GuideTone.intervalMs(now))
            }
        }
    }

    fun stop() {
        loop?.cancel()
        loop = null
    }

    /** One beep at the highest pitch, twice: "you're there". */
    fun arrived(scope: CoroutineScope) {
        scope.launch {
            play(STEPS - 1)
            delay(ARRIVED_GAP_MS)
            play(STEPS - 1)
        }
    }

    /** Settings' preview of the volume: a far beep, then a near one. */
    fun sample(scope: CoroutineScope) {
        scope.launch {
            play(0)
            delay(GuideTone.SLOWEST_MS)
            play(STEPS - 1)
        }
    }

    private fun play(step: Int) {
        val track = tracks.getOrNull(step) ?: return
        val volume = loudness()
        if (volume <= 0f) return
        runCatching {
            track.setVolume(volume)
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.reloadStaticData()
            track.play()
        }.onFailure { Log.w(TAG, "Could not play a guidance beep", it) }
    }

    fun release() {
        stop()
        tracks.forEach { track -> runCatching { track?.release() } }
    }

    private fun render(hz: Double): ShortArray {
        val length = (BEEP_SEC * SAMPLE_RATE).toInt()
        // Short ramps, not hard edges: a square envelope clicks on small speakers.
        val ramp = min(length / 4, (0.010 * SAMPLE_RATE).toInt()).coerceAtLeast(1)
        return ShortArray(length) { i ->
            val envelope = when {
                i < ramp -> i.toDouble() / ramp
                i > length - ramp -> (length - i).toDouble() / ramp
                else -> 1.0
            }
            (GAIN * envelope * sin(2 * PI * hz * i / SAMPLE_RATE) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun buildTrack(pcm: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        return track
    }

    private companion object {
        const val TAG = "VisorGuideTone"
        const val SAMPLE_RATE = 44_100
        const val STEPS = 9
        const val BEEP_SEC = 0.06
        const val GAIN = 0.6
        const val IDLE_POLL_MS = 50L
        const val ARRIVED_GAP_MS = 120L
    }
}
