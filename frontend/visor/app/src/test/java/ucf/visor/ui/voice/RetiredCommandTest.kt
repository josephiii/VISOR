package ucf.visor.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VISOR no longer has modes or sessions: each camera feature has its own
 * button and wake phrase, and works as soon as the user is logged in. What
 * used to pick a mode or start a session, and the commands that only ever
 * said they did nothing ("resend code", "next"), are not commands any more.
 */
class RetiredCommandTest {

    @Test
    fun retiredPhrasesAreNotCommands() {
        listOf(
            // Modes
            "hazard mode", "hazard awareness", "scene mode", "reading mode", "reader",
            "identify mode", "object identification",
            // Sessions
            "start session", "start a session", "begin session", "Start Session",
            "end session", "stop session", "end the session", "how do sessions work",
            // Only ever replied that they did nothing
            "resend code", "resend the code", "send code again", "next", "skip", "continue",
            // Bare "start" and "stop" used to start and end a session. "Stop"
            // is what anyone says to make speech stop.
            "start", "stop", "Stop",
        ).forEach { phrase ->
            assertEquals(phrase, VoiceCommand.Unrecognized, VoiceCommandParser.parse(phrase))
        }
    }

    @Test
    fun noSpokenCommandTakesAPicture() {
        // Pictures come from wake phrases ("VISOR, read this"), Home's buttons,
        // and the glasses' buttons, never from what is said after "VISOR GO".
        val said = VoiceCommandKind.entries.flatMap { it.examples } +
            listOf("hazard mode", "scene mode", "reading mode", "identify mode", "check for hazards", "read text")
        said.forEach { phrase ->
            val command = VoiceCommandParser.parse(phrase)
            assertTrue("\"$phrase\" parses to $command", command !is VoiceCommand.RunCamera)
        }
    }

    @Test
    fun startingSomethingElseIsStillUnderstood() {
        assertEquals(VoiceCommand.OpenHelp, VoiceCommandParser.parse("start help"))
    }
}
