package ucf.visor.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Test
import ucf.visor.data.CameraChoice

/** "VISOR GO, use my phone camera" switches cameras, without taking over nearby commands. */
class CameraChoiceCommandTest {

    @Test
    fun thePhoneCanBeChosenByVoice() {
        listOf(
            "use my phone camera",
            "use my phone's camera",
            "use my phones camera",
            "switch to the phone camera",
            "use my phone",
        ).forEach { said ->
            assertEquals(said, VoiceCommand.UseCamera(CameraChoice.PHONE), VoiceCommandParser.parse(said))
        }
    }

    @Test
    fun theGlassesCanBeChosenByVoice() {
        listOf(
            "use my glasses camera",
            "switch back to the glasses camera",
            "use my glasses",
        ).forEach { said ->
            assertEquals(said, VoiceCommand.UseCamera(CameraChoice.GLASSES), VoiceCommandParser.parse(said))
        }
    }

    @Test
    fun nearbyCommandsAreUnchanged() {
        assertEquals(VoiceCommand.PairDevice, VoiceCommandParser.parse("connect my glasses"))
        assertEquals(VoiceCommand.PairDevice, VoiceCommandParser.parse("pair glasses"))
        assertEquals(VoiceCommand.GoHome, VoiceCommandParser.parse("go home"))
    }
}
