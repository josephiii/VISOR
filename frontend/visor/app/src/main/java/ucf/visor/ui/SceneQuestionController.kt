package ucf.visor.ui.voice

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import ucf.visor.capture.SceneDescriber
import ucf.visor.stt.CommandRecognizer
import ucf.visor.tts.SpokenPhrases
import ucf.visor.tts.ThinkingBeeper

/**
 * "Ask a question" (CameraCommand.ASK_QUESTION): asks the wearer what they want
 * to know, takes a picture, and answers it. [onAsked] and [onAnswered] report
 * the same steps every camera feature reports, so Home's button shows it
 * working and the Last answer card keeps what it said; [onAnswered] is given
 * the question too, for Settings > History. [takingPicture] is
 * what it says as it takes the picture, as every camera feature does:
 * "Taking picture with your phone" with the phone's camera, and nothing (null)
 * with the glasses, whose own shutter sound says it.
 *
 * [speak] says its prompts ("What's your question?"); [sayAnswer] says the
 * answer, which is an answer like any other camera feature's: at the Answers
 * volume, what "repeat that" repeats, and, when it is long, a reading that can
 * be paused and stopped.
 */
class SceneQuestionController(
    private val recognizer: CommandRecognizer,
    private val describer: SceneDescriber,
    private val beeper: ThinkingBeeper,
    private val speak: (String) -> Unit,
    private val sayAnswer: (String) -> Unit,
    private val isSpeaking: () -> Boolean,
    private val pauseWakeListening: () -> Unit,
    private val resumeWakeListening: () -> Unit,
    private val listenDelayMs: Long = 500L,
    private val onAsked: () -> Unit = {},
    private val onAnswered: (question: String, answer: String) -> Unit = { _, _ -> },
    private val takingPicture: () -> String? = { null },
) {
    private val handler = Handler(Looper.getMainLooper())
    private var busy = false

    fun activate() {
        if (busy) return
        busy = true
        pauseWakeListening()
        speak("What's your question?")
        whenQuiet {
            handler.postDelayed(
                {
                    awaitingQuestion = true
                    recognizer.listen { question -> onQuestion(question) }
                    // A recognizer that never answers must not leave "Ask a
                    // question" busy, and the wake phrases off, for good.
                    handler.postDelayed(giveUpListening, LISTEN_TIMEOUT_MS)
                },
                listenDelayMs,
            )
        }
    }

    /** Whether a question is being listened for: its answer counts once. */
    private var awaitingQuestion = false

    private val giveUpListening = Runnable {
        if (awaitingQuestion) {
            Log.w("VISOR", "Scene question: nothing heard from the recognizer in $LISTEN_TIMEOUT_MS ms")
            recognizer.cancel()
            onQuestion(null)
        }
    }

    private fun onQuestion(question: String?) {
        if (!awaitingQuestion) return
        awaitingQuestion = false
        handler.removeCallbacks(giveUpListening)
        if (question.isNullOrBlank()) {
            speak("Sorry, I didn't catch that.")
            finish()
            return
        }
        Log.d("VISOR", "Scene question: $question")
        onAsked()
        takingPicture()?.let(speak)
        beeper.start()
        describer.ask(question) { answer ->
            beeper.stop()
            onAnswered(question, answer)
            sayAnswer(answer)
            // Listening for wake phrases again at once, as it is while any
            // other camera feature's answer is said, so "VISOR GO, stop"
            // works on a long answer from its first sentence.
            busy = false
            resumeWakeListening()
        }
    }

    private fun finish() {
        whenQuiet {
            busy = false
            resumeWakeListening()
        }
    }

    private fun whenQuiet(action: () -> Unit) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (isSpeaking() && SystemClock.elapsedRealtime() < deadline) {
                    handler.postDelayed(this, 150)
                } else {
                    action()
                }
            }
        }, 400) 
    }

    private companion object {
        /** How long a question may take to be heard, from when listening starts. */
        const val LISTEN_TIMEOUT_MS = 20_000L
    }
}
