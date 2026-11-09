package ucf.visor.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Test
import ucf.visor.ui.viewmodel.SettingsSection

/** "VISOR GO, next voice / previous voice / default voice", and "open voice settings". */
class VoiceChoiceCommandTest {

    @Test
    fun nextVoiceIsUnderstood() {
        listOf("next voice", "change voice", "change your voice", "use a different voice", "try another voice", "switch voices")
            .forEach { assertEquals(it, VoiceCommand.NextVoice, VoiceCommandParser.parse(it)) }
    }

    @Test
    fun previousVoiceIsUnderstood() {
        listOf("previous voice", "Previous voice.", "the voice before")
            .forEach { assertEquals(it, VoiceCommand.PreviousVoice, VoiceCommandParser.parse(it)) }
    }

    @Test
    fun defaultVoiceIsUnderstood() {
        listOf("default voice", "use your default voice", "original voice", "reset your voice", "switch back to my usual voice")
            .forEach { assertEquals(it, VoiceCommand.DefaultVoice, VoiceCommandParser.parse(it)) }
    }

    @Test
    fun voiceSettingsOpenSpeech() {
        assertEquals(
            VoiceCommand.OpenSettingsSection(SettingsSection.SPEECH),
            VoiceCommandParser.parse("open voice settings"),
        )
    }

    @Test
    fun goingBackIsUnchanged() {
        assertEquals(VoiceCommand.GoBack, VoiceCommandParser.parse("go back"))
        assertEquals(VoiceCommand.GoBack, VoiceCommandParser.parse("previous screen"))
    }

    @Test
    fun repliesNameTheVoice() {
        assertEquals("Voice changed to US English voice 2.", VoiceReplies.voice("US English voice 2"))
        assertEquals("Voice changed to VISOR's default voice.", VoiceReplies.voice(null))
    }
}
