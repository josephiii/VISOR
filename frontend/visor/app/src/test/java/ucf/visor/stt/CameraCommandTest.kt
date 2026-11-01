package ucf.visor.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The camera features, in the one order every list of them shows. */
class CameraCommandTest {

    @Test
    fun safetyComesFirst() {
        assertEquals(
            listOf(
                CameraCommand.CHECK_HAZARDS,
                CameraCommand.READ_TEXT,
                CameraCommand.IDENTIFY_OBJECTS,
                CameraCommand.DESCRIBE_SCENE,
            ),
            CameraCommand.entries,
        )
    }

    @Test
    fun theSuggestedPhraseIsOneVisorListensFor() {
        CameraCommand.entries.forEach { command ->
            assertTrue("$command suggests \"${command.suggested}\"", command.suggested in command.phrases)
            assertEquals(command, CameraCommand.forKeyword(command.suggested))
        }
    }
}
