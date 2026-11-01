package ucf.visor.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ucf.visor.stt.CameraCommand

/**
 * VISOR no longer has modes: each camera feature has its own button and wake
 * phrase. What used to pick a mode gets a reply saying how to ask for that
 * feature, and never takes a picture by itself.
 */
class ModeCommandTest {

    @Test
    fun whatUsedToPickAModeNamesItsFeature() {
        mapOf(
            "hazard mode" to CameraCommand.CHECK_HAZARDS,
            "hazard awareness" to CameraCommand.CHECK_HAZARDS,
            "scene mode" to CameraCommand.DESCRIBE_SCENE,
            "reading mode" to CameraCommand.READ_TEXT,
            "reader" to CameraCommand.READ_TEXT,
            "identify mode" to CameraCommand.IDENTIFY_OBJECTS,
            "object identification" to CameraCommand.IDENTIFY_OBJECTS,
        ).forEach { (phrase, command) ->
            assertEquals(phrase, VoiceCommand.ModeNotNeeded(command), VoiceCommandParser.parse(phrase))
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
}
