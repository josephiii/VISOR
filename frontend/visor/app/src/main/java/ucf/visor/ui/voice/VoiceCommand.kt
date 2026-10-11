package ucf.visor.ui.voice

import ucf.visor.ui.profile.TextScale
import ucf.visor.ui.profile.Verbosity
import ucf.visor.ui.viewmodel.SessionMode

/**
 * Every navigation intent VISOR understands. Most commonly recognized from
 * free-form speech captured after the "VISOR GO" wake phrase (see
 * VoiceNavigationController), but also emitted directly by
 * GlassesNavigationController when a button on the glasses' own display is
 * tapped/selected — one flat sealed type lets a single dispatcher (VisorLayout)
 * handle both input methods identically, with access to both the ViewModel and
 * the NavHostController, regardless of which screen the command came from.
 * Availability of a given command on the current screen is decided by the
 * dispatcher, not the parser or the glasses screen model.
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
    data class SetSpeechRate(val rate: Float) : VoiceCommand()
    object SpeakFaster : VoiceCommand()
    object SpeakSlower : VoiceCommand()
    data class SetVerbosity(val verbosity: Verbosity) : VoiceCommand()
    data class SetTextScale(val scale: TextScale) : VoiceCommand()

    object Confirm : VoiceCommand()
    object Cancel : VoiceCommand()
    object Next : VoiceCommand()
    object RepeatLast : VoiceCommand()

    object Unrecognized : VoiceCommand()
}
