package ucf.visor.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "VISOR GO, keep listening" and "VISOR GO, stop listening": background
 * listening on and off. "Background" holds "back", and "stop listening" holds
 * "stop", so these must win over going back and stopping a reading.
 */
class ListeningCommandTest {

    @Test
    fun stopListeningIsUnderstood() {
        listOf(
            "stop listening",
            "Stop listening.",
            "stop listening in the background",
            "don't listen",
            "turn off background listening",
            "stop background listening",
        ).forEach { assertEquals(it, VoiceCommand.StopListening, VoiceCommandParser.parse(it)) }
    }

    @Test
    fun keepListeningIsUnderstood() {
        listOf(
            "keep listening",
            "Keep listening in the background",
            "listen in the background",
            "listen with the screen off",
            "turn on background listening",
        ).forEach { assertEquals(it, VoiceCommand.KeepListening, VoiceCommandParser.parse(it)) }
    }

    @Test
    fun otherCommandsAreUnchanged() {
        assertEquals(VoiceCommand.StopReading, VoiceCommandParser.parse("stop"))
        assertEquals(VoiceCommand.StopReading, VoiceCommandParser.parse("stop reading"))
        assertEquals(VoiceCommand.ResumeReading, VoiceCommandParser.parse("keep reading"))
        assertEquals(VoiceCommand.ResumeReading, VoiceCommandParser.parse("keep going"))
        assertEquals(VoiceCommand.GoBack, VoiceCommandParser.parse("go back"))
        assertEquals(VoiceCommand.GoHome, VoiceCommandParser.parse("go home"))
    }
}
