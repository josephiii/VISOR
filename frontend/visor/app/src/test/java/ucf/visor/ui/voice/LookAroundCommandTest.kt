package ucf.visor.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Test
import ucf.visor.background.BackgroundPolicy
import ucf.visor.stt.CameraCommand

/** "VISOR GO, look around" and "VISOR GO, guide me to the chair". */
class LookAroundCommandTest {

    @Test
    fun lookingAroundIsUnderstood() {
        listOf("look around", "Look around.", "what's around me", "what is around me")
            .forEach { assertEquals(it, VoiceCommand.RunCamera(CameraCommand.LOOK_AROUND), VoiceCommandParser.parse(it)) }
    }

    @Test
    fun beingGuidedNamesWhatTo() {
        mapOf(
            "guide me to the chair" to "the chair",
            "Guide me to the dining table." to "the dining table",
            "can you guide me to my cup" to "my cup",
            "lead me to the door" to "the door",
            "where is the couch?" to "couch",
            "where's my phone" to "phone",
            "find the person" to "person",
            "guide me" to "",
        ).forEach { (said, what) -> assertEquals(said, VoiceCommand.GuideTo(what), VoiceCommandParser.parse(said)) }
    }

    @Test
    fun aThingNamedHomeIsStillGuidedToNotAScreen() {
        assertEquals(VoiceCommand.GuideTo("the home button"), VoiceCommandParser.parse("guide me to the home button"))
        assertEquals(VoiceCommand.GoHome, VoiceCommandParser.parse("go home"))
    }

    @Test
    fun guidanceWorksWithTheScreenOff() {
        assertEquals(BackgroundPolicy.Handler.ANYWHERE, BackgroundPolicy.handler(VoiceCommand.GuideTo("the chair")))
    }
}
