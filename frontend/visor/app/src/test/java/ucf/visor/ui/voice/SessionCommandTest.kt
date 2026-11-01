package ucf.visor.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * VISOR no longer has sessions: its camera features work as soon as the user
 * is logged in. Anything said about a session gets a reply saying so, and the
 * bare words "start" and "stop", which used to start and end one, no longer
 * do anything.
 */
class SessionCommandTest {

    @Test
    fun anythingSaidAboutASessionSaysNoneIsNeeded() {
        listOf(
            "start session", "start a session", "begin session", "Start Session",
            "end session", "stop session", "end the session", "how do sessions work",
        ).forEach { phrase ->
            assertEquals(phrase, VoiceCommand.SessionNotNeeded, VoiceCommandParser.parse(phrase))
        }
    }

    @Test
    fun startAndStopAloneNoLongerMeanAnything() {
        listOf("start", "stop", "Stop").forEach { phrase ->
            assertEquals(phrase, VoiceCommand.Unrecognized, VoiceCommandParser.parse(phrase))
        }
    }

    @Test
    fun startingSomethingElseIsStillUnderstood() {
        assertEquals(VoiceCommand.OpenHelp, VoiceCommandParser.parse("start help"))
    }
}
