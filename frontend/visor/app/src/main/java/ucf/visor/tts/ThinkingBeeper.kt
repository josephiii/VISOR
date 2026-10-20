package ucf.visor.tts

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlin.math.roundToInt


/**
 * @param volume the beeps' loudness as a fraction of the media volume (0..1,
 *   0 is off). Read whenever a tone is made, so Settings changes apply at once.
 */
class ThinkingBeeper(
    private val isSpeaking: () -> Boolean,
    private val volume: () -> Float,
    private val intervalMs: Long = 1_200L,
    private val maxDurationMs: Long = 75_000L, // safety stop, longer than the network timeouts
) {
    private val handler = Handler(Looper.getMainLooper())
    private var tone: ToneGenerator? = null
    private var running = false
    private var startedAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (SystemClock.elapsedRealtime() - startedAt > maxDurationMs) {
                stop()
                return
            }
            if (!isSpeaking()) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS)
            handler.postDelayed(this, intervalMs)
        }
    }

    fun start() {
        if (running) return
        tone = newTone()
        running = true
        startedAt = SystemClock.elapsedRealtime()
        handler.postDelayed(tick, 1_000L) // give the announcement a moment to start
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
        tone?.release()
        tone = null
    }

    /** One beep at the current volume, so Settings can play what a new volume sounds like. */
    fun beepOnce() {
        val preview = newTone() ?: return
        preview.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS)
        // startTone() returns at once; releasing now would cut the beep off.
        handler.postDelayed({ preview.release() }, BEEP_MS + 100L)
    }

    /** A tone generator at the current volume, or null when beeps are off or none can be made. */
    private fun newTone(): ToneGenerator? {
        val percent = (volume() * 100).roundToInt().coerceIn(0, 100)
        if (percent == 0) return null
        return try {
            ToneGenerator(AudioManager.STREAM_MUSIC, percent) // volume 0..100
        } catch (e: RuntimeException) {
            Log.w("VISOR", "Could not create ToneGenerator", e)
            null
        }
    }

    private companion object {
        const val BEEP_MS = 150
    }
}
