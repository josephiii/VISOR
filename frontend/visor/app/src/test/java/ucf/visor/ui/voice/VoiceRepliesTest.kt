package ucf.visor.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ucf.visor.data.SpeechRates
import ucf.visor.data.TextScale
import ucf.visor.data.Verbosity
import ucf.visor.ui.viewmodel.SettingsSection

/** What VISOR says back once a voice command has done what was asked. */
class VoiceRepliesTest {

    @Test
    fun eachPartOfSettingsIsNamed() {
        val replies = SettingsSection.entries.map(VoiceReplies::settingsSection)
        assertEquals(SettingsSection.entries.size, replies.toSet().size)
        // Said, not read: no "&" for the speech engine to stumble on.
        assertEquals("Text and display settings.", VoiceReplies.settingsSection(SettingsSection.DISPLAY))
    }

    @Test
    fun highContrastSaysWhichWayItWent() {
        assertEquals("High contrast on.", VoiceReplies.highContrast(on = true))
        assertEquals("High contrast off.", VoiceReplies.highContrast(on = false))
    }

    @Test
    fun settingsChangesSayTheNewValue() {
        assertEquals("Detail set to brief.", VoiceReplies.detail(Verbosity.BRIEF))
        assertEquals("Text size set to extra large.", VoiceReplies.textSize(TextScale.EXTRA_LARGE))
        assertTrue(VoiceReplies.speechRate(SpeechRates.Normal).contains("normal speed"))
    }
}
