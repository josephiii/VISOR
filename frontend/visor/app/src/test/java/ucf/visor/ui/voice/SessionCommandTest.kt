package ucf.visor.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "Start session" and "end session" each mean only what they say. They used
 * to be one toggle, so "start session" said during a session ended it.
 */
class SessionCommandTest {

    @Test
    fun startingPhrasesOnlyStart() {
        listOf("start session", "start a session", "begin session", "Start").forEach { phrase ->
            assertEquals(phrase, VoiceCommand.StartSession, VoiceCommandParser.parse(phrase))
        }
    }

    @Test
    fun endingPhrasesOnlyEnd() {
        listOf("end session", "stop session", "end the session", "stop").forEach { phrase ->
            assertEquals(phrase, VoiceCommand.EndSession, VoiceCommandParser.parse(phrase))
        }
    }
}
