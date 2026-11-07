package ucf.visor.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * "VISOR GO, stop / pause / resume" for a long answer being read aloud. "Stop"
 * is what anyone says to make speech stop, so it is understood on its own,
 * but never where it used to end a session.
 */
class ReadingCommandTest {

    @Test
    fun stoppingIsUnderstood() {
        listOf("stop", "Stop", "stop.", "stop it", "stop reading", "stop talking", "please stop reading", "be quiet")
            .forEach { assertEquals(it, VoiceCommand.StopReading, VoiceCommandParser.parse(it)) }
    }

    @Test
    fun pausingIsUnderstood() {
        listOf("pause", "Pause", "pause reading", "pause it")
            .forEach { assertEquals(it, VoiceCommand.PauseReading, VoiceCommandParser.parse(it)) }
    }

    @Test
    fun resumingIsUnderstood() {
        listOf("resume", "Resume", "resume reading", "unpause", "continue", "keep reading", "keep going", "carry on", "go on")
            .forEach { assertEquals(it, VoiceCommand.ResumeReading, VoiceCommandParser.parse(it)) }
    }

    @Test
    fun endingASessionIsStillNotStoppingSpeech() {
        listOf("stop session", "end session", "end the session")
            .forEach { assertEquals(it, VoiceCommand.Unrecognized, VoiceCommandParser.parse(it)) }
    }

    @Test
    fun otherCommandsAreUnchanged() {
        assertEquals(VoiceCommand.Cancel, VoiceCommandParser.parse("cancel"))
        assertEquals(VoiceCommand.SpeakSlower, VoiceCommandParser.parse("speak slower"))
        assertEquals(VoiceCommand.GoHome, VoiceCommandParser.parse("go home"))
        assertEquals(VoiceCommand.RepeatLast, VoiceCommandParser.parse("repeat that"))
        assertNotEquals(VoiceCommand.StopReading, VoiceCommandParser.parse("bus stop sign"))
    }
}
