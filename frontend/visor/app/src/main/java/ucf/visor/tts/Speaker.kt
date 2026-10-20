package ucf.visor.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import ucf.visor.data.VoiceCategory
import ucf.visor.data.Volumes
import java.util.Locale

/**
 * @param loudness how loud each kind of speech plays, as a fraction of the
 *   media volume (0 is silent). Read each time something is spoken, so a
 *   change is heard on the very next word.
 * @param leadInMs how long until a chime that is still playing ends: speech
 *   waits for it rather than talking over it (see [Chimes]).
 */
class Speaker(
    context: Context,
    private val loudness: (VoiceCategory) -> Float,
    private val leadInMs: () -> Long = { 0L },
) {

    /** Something VISOR said, and which kind of speech it was. */
    data class Utterance(val text: String, val category: VoiceCategory)

    private var tts: TextToSpeech? = null
    private var isReady = false
    private val pending = mutableListOf<Pair<String, () -> Float>>()

    /**
     * What voice navigation's "repeat that" says again: the last answer or
     * session update VISOR said out loud. Not its replies to voice commands,
     * or "repeat that" would repeat the "Listening." it had just said.
     */
    var lastUtterance: Utterance? = null
        private set

    private var speechRate = 1.0f

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(speechRate)
                isReady = true
                pending.forEach { (text, volume) -> queueSpeaking(text, volume()) }
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

    /**
     * Says [text] at its [category]'s volume. When that category is silenced
     * it says nothing, but still stops whatever VISOR was saying, as any new
     * words would have.
     */
    fun speak(text: String, category: VoiceCategory) {
        if (text.isBlank()) return
        if (category != VoiceCategory.VOICE_COMMANDS && !Volumes.isOff(loudness(category))) {
            lastUtterance = Utterance(text, category)
        }
        say(text) { loudness(category) }
    }

    /**
     * Says [text] at [volume], whatever the sliders are set to: for Settings
     * saying what a slider was just set to, and the head-motion lab's cues.
     * Never what "repeat that" repeats.
     */
    fun speakAt(text: String, volume: Float) {
        if (text.isBlank()) return
        say(text) { volume }
    }

    private fun say(text: String, volume: () -> Float) {
        if (!isReady) {
            pending.add(text to volume)
            return
        }
        queueSpeaking(text, volume())
    }

    // this is what is responsible for speaking wtv is passed to speak()
    private fun queueSpeaking(text: String, volume: Float) {
        val engine = tts ?: return
        if (Volumes.isOff(volume)) {
            engine.stop()
            return
        }
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
        val wait = leadInMs()
        if (wait > 0) {
            // The wait goes in the speech queue itself, so isSpeaking() covers
            // it and the next thing said still cuts off both.
            engine.playSilentUtterance(wait, TextToSpeech.QUEUE_FLUSH, UTTERANCE_ID)
            engine.speak(text, TextToSpeech.QUEUE_ADD, params, UTTERANCE_ID)
        } else {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
        }
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

    private companion object {
        const val UTTERANCE_ID = "VISOR_SPEAKING"
    }
}
