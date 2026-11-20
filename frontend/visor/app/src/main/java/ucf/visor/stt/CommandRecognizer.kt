package ucf.visor.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.os.Looper
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Captures one free-form spoken utterance via Android's built-in SpeechRecognizer.
 *
 * This is the "what did they say" half of voice navigation: [Listener] only spots a
 * handful of fixed wake phrases baked into a tiny on-device model, so it can't
 * understand arbitrary commands like "open settings" or "go home". Once a wake
 * phrase hands off here, this class listens for a single utterance, with no fixed
 * vocabulary, and returns the recognized text (or null on error/timeout/silence)
 * for a caller to interpret.
 */
class CommandRecognizer(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    private val handler = Handler(Looper.getMainLooper())

    /** Counts listens (and cancels): a callback from any but the current one is ignored. */
    private var generation = 0

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Starts listening for one utterance. [onResult] is called exactly once, with
     * the best-guess recognized text, or null if nothing usable was heard.
     *
     * The microphone only opens some time after this is called, 0.6 to 0.9 s on
     * most phones, which often play a tone of their own as it does. [onReady]
     * says when it has opened and the user can speak, and [onSpeechEnded] when
     * the user has stopped and the recognizer is working out what was said.
     */
    fun listen(
        onReady: () -> Unit = {},
        onSpeechEnded: () -> Unit = {},
        onResult: (String?) -> Unit,
    ) {
        if (!isAvailable()) {
            Log.w("VISOR CommandRecognizer", "Speech recognition unavailable on this device")
            onResult(null)
            return
        }

        cancel()
        start(onReady, onSpeechEnded, onResult, retried = false)
    }

    private fun start(
        onReady: () -> Unit,
        onSpeechEnded: () -> Unit,
        onResult: (String?) -> Unit,
        retried: Boolean,
    ) {
        val mine = ++generation
        val startedAt = SystemClock.elapsedRealtime()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r

        // This listen is over: nothing more is heard from its recognizer, which
        // is let go (not from inside its own callback).
        fun end(): Boolean {
            if (mine != generation) return false
            generation++
            recognizer = null
            handler.post { runCatching { r.destroy() } }
            return true
        }

        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results
                    .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (end()) onResult(text)
            }

            override fun onError(error: Int) {
                Log.d("VISOR CommandRecognizer", "Recognition error: $error")
                // Android can report an error more than once, and a recognizer
                // that was replaced reports one late: only the listen in
                // progress counts, and only once. Before this, a second "Ask a
                // question" got the first one's error, said "Sorry, I didn't
                // catch that" over its own listening, and left the button and
                // the wake phrases stuck (field log, 2026-11-15).
                val early = SystemClock.elapsedRealtime() - startedAt < RETRY_WITHIN_MS
                if (!retried && early && error in RETRY_ERRORS) {
                    // The speech service wasn't ready (still letting go of the
                    // last recognizer): once more, a moment later.
                    if (!end()) return
                    val retry = ++generation
                    handler.postDelayed({
                        if (retry == generation) start(onReady, onSpeechEnded, onResult, retried = true)
                    }, RETRY_DELAY_MS)
                    return
                }
                if (end()) onResult(null)
            }

            // Overridden functions to be updated as necessary...
            override fun onPartialResults(partialResults: Bundle) = Unit
            override fun onReadyForSpeech(params: Bundle?) { if (mine == generation) onReady() }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { if (mine == generation) onSpeechEnded() }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        r.startListening(intent)
    }

    /** Stops any in-flight recognition without delivering a result. */
    fun cancel() {
        // Whatever the old recognizer (or a retry waiting its turn) says now is ignored.
        generation++
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    fun shutdown() {
        cancel()
    }

    private companion object {
        /** Errors worth one more try when they come at once: the speech service was busy or gone. */
        val RETRY_ERRORS = setOf(SpeechRecognizer.ERROR_RECOGNIZER_BUSY, ERROR_SERVER_DISCONNECTED)

        /** SpeechRecognizer.ERROR_SERVER_DISCONNECTED, which only has a name from Android 12. */
        const val ERROR_SERVER_DISCONNECTED = 11
        const val RETRY_WITHIN_MS = 1_500L
        const val RETRY_DELAY_MS = 400L
    }
}
