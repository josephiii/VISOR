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
            "open color settings" to SettingsSection.COLORS,
            "theme settings" to SettingsSection.COLORS,
        ).forEach { (said, section) ->
            assertEquals(said, VoiceCommand.OpenSettingsSection(section), VoiceCommandParser.parse(said))
        }
    }

    @Test
    fun historyOpensWithOrWithoutSettings() {
        listOf(
            "open history",
            "history",
            "open history settings",
            "show my past answers",
            "today's answers",
            "go back to my previous answers",
        ).forEach { said ->
            assertEquals(said, VoiceCommand.OpenSettingsSection(SettingsSection.HISTORY), VoiceCommandParser.parse(said))
        }
    }

    @Test
    fun settingsAloneOpensTheHub() {
        assertEquals(VoiceCommand.OpenSettings, VoiceCommandParser.parse("open settings"))
        assertEquals(VoiceCommand.OpenSettings, VoiceCommandParser.parse("settings"))
    }
}
