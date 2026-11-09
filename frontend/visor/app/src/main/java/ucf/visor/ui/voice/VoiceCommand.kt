package ucf.visor.ui.voice

import ucf.visor.data.CameraChoice
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
     * One of Home's camera buttons, such as "Check for hazards": a picture,
     * and [command]'s answer. Offered on the glasses' own display.
     */
    data class RunCamera(val command: CameraCommand) : VoiceCommand()

    object GoToLogin : VoiceCommand()
    object GoToSignUp : VoiceCommand()
    object GoToForgotPassword : VoiceCommand()

    object LogOut : VoiceCommand()
    object DeleteAccount : VoiceCommand()
    object ToggleHighContrast : VoiceCommand()

    /** "Use my phone camera": the camera every camera feature uses, as on the Pair screen. */
    data class UseCamera(val camera: CameraChoice) : VoiceCommand()
    data class SetSpeechRate(val rate: Float) : VoiceCommand()
    object SpeakFaster : VoiceCommand()
    object SpeakSlower : VoiceCommand()

    /**
     * VISOR's voice (tts/VoiceCatalog): the next or previous voice that can
     * be used now, in the order Settings lists them, or VISOR's default. The
     * reply is said in the new voice, so it is heard as it is chosen.
     */
    object NextVoice : VoiceCommand()
    object PreviousVoice : VoiceCommand()
    object DefaultVoice : VoiceCommand()
    data class SetVerbosity(val verbosity: Verbosity) : VoiceCommand()
    data class SetTextScale(val scale: TextScale) : VoiceCommand()

    object Confirm : VoiceCommand()
    object Cancel : VoiceCommand()
    object RepeatLast : VoiceCommand()

    /**
     * For a long answer VISOR is reading aloud (tts/Readings): "stop" ends it,
     * "pause" keeps it paused, and "resume" carries on from the start of the
     * sentence it stopped in. Saying "VISOR GO" over a reading already pauses
     * it, so the reading is never lost to the "Listening." before these.
     * Offered on the glasses' display while VISOR reads.
     */
    object StopReading : VoiceCommand()
    object PauseReading : VoiceCommand()
    object ResumeReading : VoiceCommand()

    object Unrecognized : VoiceCommand()
}
