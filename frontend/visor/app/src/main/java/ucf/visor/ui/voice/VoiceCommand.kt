package ucf.visor.ui.voice

import ucf.visor.data.TextScale
import ucf.visor.data.Verbosity
import ucf.visor.stt.CameraCommand
import ucf.visor.ui.viewmodel.SettingsSection

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

    /** "Open sound settings": straight to one part of Settings. */
    data class OpenSettingsSection(val section: SettingsSection) : VoiceCommand()
    object OpenHelp : VoiceCommand()
    object PairDevice : VoiceCommand()
    object GoBack : VoiceCommand()

    /**
     * Anything said about a session, such as "start session". VISOR no longer
     * has sessions (its camera features work as soon as the user is logged
     * in), so this only says so, for anyone still in the habit.
     */
    object SessionNotNeeded : VoiceCommand()

    /**
     * "Hazard mode", "reading mode"... VISOR no longer has modes: each camera
     * feature has its own button and wake phrase. This says how to ask for
     * [command] directly, rather than taking a picture nobody expected.
     */
    data class ModeNotNeeded(val command: CameraCommand) : VoiceCommand()

    /**
     * One of Home's camera buttons, such as "Check for hazards": a picture,
     * and [command]'s answer. Offered on the glasses' own display.
     */
    data class RunCamera(val command: CameraCommand) : VoiceCommand()

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
