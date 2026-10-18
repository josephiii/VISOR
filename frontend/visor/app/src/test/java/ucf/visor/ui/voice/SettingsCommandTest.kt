package ucf.visor.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Test
import ucf.visor.ui.viewmodel.SettingsSection

/** "VISOR GO, open … settings" opens that part of Settings, or the Settings hub. */
class SettingsCommandTest {

    @Test
    fun eachPartOfSettingsCanBeOpenedByName() {
        mapOf(
            "open sound settings" to SettingsSection.SOUND,
            "volume settings" to SettingsSection.SOUND,
            "speech settings" to SettingsSection.SPEECH,
            "open display settings" to SettingsSection.DISPLAY,
            "text settings" to SettingsSection.DISPLAY,
            "command settings" to SettingsSection.COMMANDS,
            "account settings" to SettingsSection.ACCOUNT,
            "open settings about me" to SettingsSection.ABOUT_YOU,
        ).forEach { (said, section) ->
            assertEquals(said, VoiceCommand.OpenSettingsSection(section), VoiceCommandParser.parse(said))
        }
    }

    @Test
    fun settingsAloneOpensTheHub() {
        assertEquals(VoiceCommand.OpenSettings, VoiceCommandParser.parse("open settings"))
        assertEquals(VoiceCommand.OpenSettings, VoiceCommandParser.parse("settings"))
    }
}
