package ucf.visor.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When VISOR counts as speaking, so a setup question is finished before the
 * microphone opens (VoiceNavigationController.captureUtterance).
 */
class QueuedSpeechTest {

    private var clock = 0L
    private val speech = QueuedSpeech { clock }

    @Test
    fun `nothing queued is not speaking`() {
        assertFalse(speech.isSpeaking(engineSpeaking = false))
    }

    // The bug: the engine says "not speaking" until the sound starts, so the
    // microphone opened over the question it had just been asked to say.
    @Test
    fun `speaking as soon as it is queued, before the engine starts the sound`() {
        speech.queued("q1")
        clock += 800
        assertTrue(speech.isSpeaking(engineSpeaking = false))
    }

    @Test
    fun `speaking while the engine says so, however long the question takes to say`() {
        speech.queued("q1")
        clock += 30_000 // a long question at a slow speech rate
        assertTrue(speech.isSpeaking(engineSpeaking = true))
    }

    @Test
    fun `done once the engine reports the last thing queued said`() {
        speech.queued("q1")
        speech.ended("q1")
        assertFalse(speech.isSpeaking(engineSpeaking = false))
    }

    @Test
    fun `a report about something said before the last is not the end`() {
        speech.queued("listening")
        speech.queued("question") // flushed "listening", whose stop is reported next
        speech.ended("listening")
        assertTrue(speech.isSpeaking(engineSpeaking = false))
    }

    @Test
    fun `stopped is not speaking`() {
        speech.queued("q1")
        speech.cleared()
        assertFalse(speech.isSpeaking(engineSpeaking = false))
    }

    @Test
    fun `an engine that never starts or reports can't keep VISOR speaking`() {
        speech.queued("q1")
        clock += QueuedSpeech.StartGraceMs
        assertFalse(speech.isSpeaking(engineSpeaking = false))
    }
}
