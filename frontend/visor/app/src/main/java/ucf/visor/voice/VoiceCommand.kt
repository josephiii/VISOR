package ucf.visor.voice

import ucf.visor.ui.profile.SpeechRate
import ucf.visor.ui.profile.TextScale
import ucf.visor.ui.profile.Verbosity
import ucf.visor.ui.viewmodel.SessionMode

/**
 * Every voice-navigation intent VISOR understands, recognized from free-form
 * speech captured after the "VISOR GO" wake phrase (see VoiceNavigationController).
 * Kept as one flat sealed type so a single dispatcher (VisorLayout) can switch on
 * it with access to both the ViewModel and the NavHostController, regardless of
 * which screen the utterance was spoken on. Availability of a given command on
 * the current screen is decided by the dispatcher, not the parser.
 */
sealed class VoiceCommand {
    object GoHome : VoiceCommand()
    object OpenSettings : VoiceCommand()
    object OpenHelp : VoiceCommand()
    object PairDevice : VoiceCommand()
    object GoBack : VoiceCommand()

    object ToggleSession : VoiceCommand()
    data class SwitchMode(val mode: SessionMode) : VoiceCommand()

    object GoToLogin : VoiceCommand()
    object GoToSignUp : VoiceCommand()
    object GoToForgotPassword : VoiceCommand()
    object ResendCode : VoiceCommand()

    object LogOut : VoiceCommand()
    object DeleteAccount : VoiceCommand()
    object ToggleHighContrast : VoiceCommand()
    data class SetSpeechRate(val rate: SpeechRate) : VoiceCommand()
    data class SetVerbosity(val verbosity: Verbosity) : VoiceCommand()
    data class SetTextScale(val scale: TextScale) : VoiceCommand()

    object Confirm : VoiceCommand()
    object Cancel : VoiceCommand()
    object Next : VoiceCommand()
    object RepeatLast : VoiceCommand()

    object Unrecognized : VoiceCommand()
}
