package ucf.visor.ui.voice

import ucf.visor.data.CameraChoice
import ucf.visor.data.SpeechRates
import ucf.visor.data.TextScale
import ucf.visor.data.Verbosity
import ucf.visor.ui.viewmodel.SettingsSection

/**
 * Turns the free-form text recognized after "VISOR GO" into a [VoiceCommand].
 *
 * Deliberately simple substring matching, not full NLU: the vocabulary is small
 * and fixed (it mirrors the app's own button labels), and every command has an
 * on-screen equivalent a sighted/touch user could tap instead, so false negatives
 * (falling through to [VoiceCommand.Unrecognized]) are far less harmful than a
 * misfire on a destructive action. Matching is intentionally lenient on phrasing
 * ("log out" / "sign out" / "logout") but never on which words are present.
 */
object VoiceCommandParser {

    fun parse(rawText: String): VoiceCommand {
        val text = rawText.trim().lowercase()
        if (text.isEmpty()) return VoiceCommand.Unrecognized

        return when {
            containsAny(text, "go home", "home screen", "home") -> VoiceCommand.GoHome
            containsAny(text, "settings", "setting") ->
                settingsSectionIn(text)?.let(VoiceCommand::OpenSettingsSection) ?: VoiceCommand.OpenSettings
            containsAny(text, "help") -> VoiceCommand.OpenHelp
            containsAny(text, "pair", "hardware", "connect my glasses", "connect glasses") ->
                VoiceCommand.PairDevice

            // Which camera takes the pictures. Ahead of "back", for "switch back
            // to my glasses camera", and of the camera features' own words.
            containsAny(text, "phone camera", "phone's camera", "phones camera", "use my phone", "use the phone") ->
                VoiceCommand.UseCamera(CameraChoice.PHONE)
            containsAny(text, "glasses camera", "glasses' camera", "use my glasses", "use the glasses") ->
                VoiceCommand.UseCamera(CameraChoice.GLASSES)

            containsAny(text, "go back", "back", "previous screen") -> VoiceCommand.GoBack

            // Nothing here takes a picture or picks a "mode" ("hazard mode"):
            // VISOR has no modes or sessions any more. Pictures come from the
            // camera wake phrases ("VISOR, read this") and Home's buttons.

            containsAny(
                text,
                "sign up",
                "signup",
                "register",
                "new account"
            ) -> VoiceCommand.GoToSignUp

            containsAny(text, "log in", "login") -> VoiceCommand.GoToLogin
            containsAny(
                text,
                "forgot password",
                "forgot my password"
            ) -> VoiceCommand.GoToForgotPassword

            containsAny(text, "log out", "sign out", "logout") -> VoiceCommand.LogOut
            containsAny(text, "delete my account", "delete account") -> VoiceCommand.DeleteAccount
            containsAny(text, "high contrast") -> VoiceCommand.ToggleHighContrast
            containsAny(text, "normal speed", "normal speech") ->
                VoiceCommand.SetSpeechRate(SpeechRates.Normal)

            containsAny(text, "slower", "speak slower", "talk slower") ->
                VoiceCommand.SpeakSlower

            containsAny(text, "faster", "speak faster", "talk faster") ->
                VoiceCommand.SpeakFaster

            containsAny(text, "brief") -> VoiceCommand.SetVerbosity(Verbosity.BRIEF)
            containsAny(
                text,
                "detailed",
                "more detail"
            ) -> VoiceCommand.SetVerbosity(Verbosity.DETAILED)

            containsAny(text, "standard detail", "standard verbosity") ->
                VoiceCommand.SetVerbosity(Verbosity.STANDARD)

            containsAny(text, "extra large text", "extra large") ->
                VoiceCommand.SetTextScale(TextScale.EXTRA_LARGE)

            containsAny(
                text,
                "large text",
                "bigger text"
            ) -> VoiceCommand.SetTextScale(TextScale.LARGE)

            containsAny(
                text,
                "standard text",
                "normal text"
            ) -> VoiceCommand.SetTextScale(TextScale.STANDARD)

            // A long answer being read aloud (see VoiceCommand.StopReading).
            // "Stop" and "continue" alone, or about the reading: "stop session"
            // and "end session" are not commands any more (RetiredCommandTest).
            isOnly(text, "stop", "stop it", "be quiet", "quiet", "shush") ||
                containsAny(text, "stop reading", "stop talking", "stop speaking") ->
                VoiceCommand.StopReading

            containsWord(text, "pause") -> VoiceCommand.PauseReading

            isOnly(text, "continue", "go on", "play") ||
                containsWord(text, "resume", "unpause") ||
                containsAny(text, "keep reading", "keep going", "carry on", "continue reading") ->
                VoiceCommand.ResumeReading

            containsAny(text, "confirm", "okay") || containsWord(
                text,
                "yes",
                "ok"
            ) -> VoiceCommand.Confirm

            containsAny(text, "cancel", "never mind", "nevermind") || containsWord(text, "no") ->
                VoiceCommand.Cancel

            containsAny(text, "say that again", "what did you say") || containsWord(
                text,
                "repeat"
            ) ->
                VoiceCommand.RepeatLast

            else -> VoiceCommand.Unrecognized
        }
    }

    /** Which part of Settings "open … settings" names, if any. */
    private fun settingsSectionIn(text: String): SettingsSection? = when {
        containsAny(text, "sound", "volume", "loud", "vibration") -> SettingsSection.SOUND
        containsAny(text, "speech", "speed", "talk") -> SettingsSection.SPEECH
        containsAny(text, "display", "text", "contrast", "dark", "light") -> SettingsSection.DISPLAY
        containsAny(text, "command") -> SettingsSection.COMMANDS
        containsAny(text, "account") -> SettingsSection.ACCOUNT
        containsAny(text, "about", "profile", "vision", "name") -> SettingsSection.ABOUT_YOU
        else -> null
    }

    /** Whether all that was said is one of [phrases], give or take a full stop. */
    private fun isOnly(text: String, vararg phrases: String): Boolean =
        text.trimEnd('.', '!', '?', ' ') in phrases

    private fun containsAny(text: String, vararg phrases: String): Boolean =
        phrases.any { text.contains(it) }

    /** Whole-word match — avoids e.g. "broken" or "know" false-matching "ok"/"no". */
    private fun containsWord(text: String, vararg words: String): Boolean =
        words.any { word -> Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(text) }
}

/**
 * What can be said after "VISOR GO", by kind, as Settings lists it. Only
 * phrases [VoiceCommandParser] understands: a unit test parses every one, so
 * Settings never suggests something VISOR won't act on.
 */
enum class VoiceCommandKind(val examples: List<String>) {
    GO_PLACES(listOf("go home", "open settings", "open sound settings", "help", "pair glasses", "go back")),
    SETTINGS(
        listOf(
            "faster", "slower", "normal speed", "brief", "detailed", "standard detail",
            "large text", "extra large text", "normal text", "high contrast",
            "use my phone camera", "use my glasses camera",
        )
    ),
    ACCOUNT(listOf("log out", "delete my account")),
    ANSWERING(listOf("yes", "no", "repeat that")),
    READING(listOf("stop", "pause", "resume")),
}
