package ucf.visor.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Captures one free-form spoken utterance via Android's built-in SpeechRecognizer.
 *
 * This is the "what did they say" half of voice navigation: [Listener] only spots a
 * handful of fixed wake phrases baked into a tiny on-device model, so it can't
 * understand arbitrary commands like "open settings" or "hazard mode". Once a wake
 * phrase hands off here, this class listens for a single utterance, with no fixed
 * vocabulary, and returns the recognized text (or null on error/timeout/silence)
 * for a caller to interpret.
 */
class CommandRecognizer(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** Starts listening for one utterance. [onResult] is called exactly once, with
     *  the best-guess recognized text, or null if nothing usable was heard. */
    fun listen(onResult: (String?) -> Unit) {
        if (!isAvailable()) {
            Log.w("VISOR CommandRecognizer", "Speech recognition unavailable on this device")
            onResult(null)
            return
        }

        cancel()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r

        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results
                    .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                onResult(text)
            }

            override fun onError(error: Int) {
                Log.d("VISOR CommandRecognizer", "Recognition error: $error")
                onResult(null)
            }

            // Overridden functions to be updated as necessary...
            override fun onPartialResults(partialResults: Bundle) = Unit
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
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
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    fun shutdown() {
        cancel()
    }
}
