package ucf.visor.ui.voice

import ucf.visor.ui.profile.SpeechRates
import ucf.visor.ui.profile.TextScale
import ucf.visor.ui.profile.Verbosity
import ucf.visor.ui.viewmodel.SessionMode

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
            containsAny(text, "settings", "setting") -> VoiceCommand.OpenSettings
            containsAny(text, "help") -> VoiceCommand.OpenHelp
            containsAny(text, "pair", "hardware", "connect my glasses", "connect glasses") ->
                VoiceCommand.PairDevice

            containsAny(text, "go back", "back", "previous screen") -> VoiceCommand.GoBack

            containsAny(text, "start session", "end session", "stop session", "start", "stop") ->
                VoiceCommand.ToggleSession

            containsAny(text, "hazard") -> VoiceCommand.SwitchMode(SessionMode.HAZARD)
            containsAny(text, "scene") -> VoiceCommand.SwitchMode(SessionMode.SCENE)
            containsAny(text, "reader", "reading") -> VoiceCommand.SwitchMode(SessionMode.READER)

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

            containsAny(
                text,
                "resend code",
                "resend the code",
                "send code again"
            ) -> VoiceCommand.ResendCode

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

            containsAny(text, "confirm", "okay") || containsWord(
                text,
                "yes",
                "ok"
            ) -> VoiceCommand.Confirm

            containsAny(text, "cancel", "never mind", "nevermind") || containsWord(text, "no") ->
                VoiceCommand.Cancel

            containsAny(text, "continue", "skip") || containsWord(text, "next") -> VoiceCommand.Next
            containsAny(text, "say that again", "what did you say") || containsWord(
                text,
                "repeat"
            ) ->
                VoiceCommand.RepeatLast

            else -> VoiceCommand.Unrecognized
        }
    }

    private fun containsAny(text: String, vararg phrases: String): Boolean =
        phrases.any { text.contains(it) }

    /** Whole-word match — avoids e.g. "broken" or "know" false-matching "ok"/"no". */
    private fun containsWord(text: String, vararg words: String): Boolean =
        words.any { word -> Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(text) }
}
