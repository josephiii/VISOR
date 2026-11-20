package ucf.visor.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Test
import ucf.visor.ui.viewmodel.SettingsSection

/**
 * "VISOR GO, use my colors" and "use VISOR colors". The second is the way
 * back from colors that turned out hard to see, so it must be understood
 * however it is put, and never be taken for something else.
 */
class ColorsCommandTest {

    @Test
    fun visorColorsComeBackHoweverItIsSaid() {
        listOf(
            "use visor colors",
            "use VISOR's colours",
            "switch back to visor colors",
            "go back to the default colors",
            "reset colors",
            "reset my colors",
            "normal colors please",
            "use the original colors",
        ).forEach { said ->
            assertEquals(said, VoiceCommand.UseVisorColors, VoiceCommandParser.parse(said))
        }
    }

    @Test
    fun myColorsCanBeTurnedOn() {
        listOf(
            "use my colors",
            "use my own colours",
            "switch to my colors",
            "custom colors",
            "use my theme",
        ).forEach { said ->
            assertEquals(said, VoiceCommand.UseCustomColors, VoiceCommandParser.parse(said))
        }
    }

    @Test
    fun changingColorsOpensTheEditor() {
        listOf(
            "change my colors",
            "edit my colors",
            "pick colors",
            "open color settings",
        ).forEach { said ->
            assertEquals(said, VoiceCommand.OpenSettingsSection(SettingsSection.COLORS), VoiceCommandParser.parse(said))
        }
    }

    /** "Back" alone still goes back; "high contrast" still toggles it. */
    @Test
    fun colorsDoNotTakeOverOtherCommands() {
        assertEquals(VoiceCommand.GoBack, VoiceCommandParser.parse("go back"))
        assertEquals(VoiceCommand.ToggleHighContrast, VoiceCommandParser.parse("high contrast"))
        assertEquals(VoiceCommand.OpenSettingsSection(SettingsSection.DISPLAY), VoiceCommandParser.parse("open display settings"))
    }
}
