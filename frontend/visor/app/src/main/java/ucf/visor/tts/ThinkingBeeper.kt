package ucf.visor.tts

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log


class ThinkingBeeper(
    private val isSpeaking: () -> Boolean,
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
            if (!isSpeaking()) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            handler.postDelayed(this, intervalMs)
        }
    }

    fun start() {
        if (running) return
        tone = try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 60) // volume 0..100
        } catch (e: RuntimeException) {
            Log.w("VISOR", "Could not create ToneGenerator", e)
            null
        }
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
}