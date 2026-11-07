package ucf.visor.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ucf.visor.data.TextScale
import ucf.visor.data.Verbosity

/**
 * Settings lists what can be said after "VISOR GO". Every phrase it lists must
 * be one VISOR acts on, and act on as the kind it is listed under.
 */
class VoiceCommandKindTest {

    @Test
    fun everyListedPhraseIsUnderstoodAsItsKind() {
        VoiceCommandKind.entries.forEach { kind ->
            kind.examples.forEach { phrase ->
                val command = VoiceCommandParser.parse(phrase)
                assertTrue(
                    "\"$phrase\", listed under $kind, parses to $command",
                    command.isOfKind(kind),
                )
            }
        }
    }

    @Test
    fun settingsCanBeSetBackToStandardFromWhatIsListed() {
        // "Brief" and "large text" are listed, so the way back must be too.
        val listed = VoiceCommandKind.SETTINGS.examples.map(VoiceCommandParser::parse)
        assertTrue(VoiceCommand.SetVerbosity(Verbosity.STANDARD) in listed)
        assertTrue(VoiceCommand.SetTextScale(TextScale.STANDARD) in listed)
        assertEquals(VoiceCommand.SetVerbosity(Verbosity.STANDARD), VoiceCommandParser.parse("standard detail"))
        assertEquals(VoiceCommand.SetTextScale(TextScale.STANDARD), VoiceCommandParser.parse("normal text"))
    }

    private fun VoiceCommand.isOfKind(kind: VoiceCommandKind): Boolean = when (kind) {
        VoiceCommandKind.GO_PLACES -> this == VoiceCommand.GoHome ||
            this == VoiceCommand.OpenSettings ||
            this is VoiceCommand.OpenSettingsSection ||
            this == VoiceCommand.OpenHelp ||
            this == VoiceCommand.PairDevice ||
            this == VoiceCommand.GoBack

        VoiceCommandKind.SETTINGS -> this == VoiceCommand.ToggleHighContrast ||
            this is VoiceCommand.SetSpeechRate ||
            this == VoiceCommand.SpeakFaster ||
            this == VoiceCommand.SpeakSlower ||
            this is VoiceCommand.SetVerbosity ||
            this is VoiceCommand.SetTextScale ||
            this is VoiceCommand.UseCamera

        VoiceCommandKind.ACCOUNT ->
            this == VoiceCommand.LogOut || this == VoiceCommand.DeleteAccount

        VoiceCommandKind.ANSWERING -> this == VoiceCommand.Confirm ||
            this == VoiceCommand.Cancel ||
            this == VoiceCommand.RepeatLast
    }
}
