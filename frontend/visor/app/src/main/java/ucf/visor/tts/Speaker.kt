package ucf.visor.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class Speaker(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private val pending = mutableListOf<String>()

    // Tracks the last thing spoken so voice navigation can honor "repeat that".
    var lastUtterance: String? = null
        private set

    private var speechRate = 1.0f

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(speechRate)
                isReady = true
                pending.forEach { queueSpeaking(it) }
                pending.clear()
            } else {
                Log.e("VISOR TTS", "TTS Failed: $status")
            }

        }
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate
        tts?.setSpeechRate(rate)
    }

    // this is called to use tts
    fun speak(text: String) {
        if (text.isBlank()) return
        lastUtterance = text
        if (!isReady) {
            pending.add(text)
            return
        }
        queueSpeaking(text)
    }

    // this is what is responsible for speaking wtv is passed to speak()
    private fun queueSpeaking(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "VISOR_SPEAKING")
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    // interrupts speech w out killing engine, like a cancel btn mid-sentence
    fun stop() {
        tts?.stop()
    }

    // kills whole engine, should b used in OnDestroy in MainActivity
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()

    }
}