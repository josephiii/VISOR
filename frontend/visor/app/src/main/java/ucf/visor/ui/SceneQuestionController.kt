package ucf.visor.ui.voice

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import ucf.visor.capture.SceneDescriber
import ucf.visor.stt.CommandRecognizer
import ucf.visor.tts.ThinkingBeeper

class SceneQuestionController(
    private val recognizer: CommandRecognizer,
    private val describer: SceneDescriber,
    private val beeper: ThinkingBeeper,
    private val speak: (String) -> Unit,
    private val isSpeaking: () -> Boolean,
    private val pauseWakeListening: () -> Unit,
    private val resumeWakeListening: () -> Unit,
    private val listenDelayMs: Long = 500L,
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
                { recognizer.listen { question -> onQuestion(question) } },
                listenDelayMs,
            )
        }
    }

    private fun onQuestion(question: String?) {
        if (question.isNullOrBlank()) {
            speak("Sorry, I didn't catch that.")
            finish()
            return
        }
        Log.d("VISOR", "Scene question: $question")
        speak("Taking picture")
        beeper.start()
        describer.ask(question) { answer ->
            beeper.stop()
            speak(answer)
            finish()
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
}