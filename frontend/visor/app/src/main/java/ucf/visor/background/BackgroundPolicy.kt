package ucf.visor.background

import ucf.visor.ui.voice.VoiceCommand

/**
 * Which voice and glasses commands VISOR can carry out with no screen showing,
 * as with the screen off and the phone in a pocket (background listening).
 *
 * One rule for every command, in the style of `CameraCommand.availability`:
 * VisorEngine asks it for each command, whether it came from "VISOR GO" or a
 * tap on the glasses, and a unit test checks every command has an answer.
 */
object BackgroundPolicy {

    enum class Handler {
        /** Needs no screen: VisorEngine carries it out, screen or not. */
        ANYWHERE,

        /**
         * Changes or moves between screens, so only while VISOR is on screen
         * (VisorLayout carries it out). Otherwise VISOR says to open it:
         * [NEEDS_SCREEN].
         */
        ON_SCREEN,
    }

    /** What VISOR says to a screen-only command with no screen showing. */
    const val NEEDS_SCREEN = "Open VISOR on your phone to do that."

    fun handler(command: VoiceCommand): Handler = when (command) {
        // Pictures and answers: what background listening is for.
        is VoiceCommand.RunCamera,
        is VoiceCommand.GuideTo,
        VoiceCommand.RepeatLast,
        VoiceCommand.StopReading,
        VoiceCommand.PauseReading,
        VoiceCommand.ResumeReading,
        // Settings that change how VISOR sounds or which camera it uses.
        is VoiceCommand.UseCamera,
        is VoiceCommand.SetSpeechRate,
        VoiceCommand.SpeakFaster,
        VoiceCommand.SpeakSlower,
        VoiceCommand.NextVoice,
        VoiceCommand.PreviousVoice,
        VoiceCommand.DefaultVoice,
        is VoiceCommand.SetVerbosity,
        // Turning background listening itself on and off.
        VoiceCommand.KeepListening,
        VoiceCommand.StopListening,
        VoiceCommand.Unrecognized -> Handler.ANYWHERE

        // Screens, and what is seen on them.
        VoiceCommand.GoHome,
        VoiceCommand.OpenSettings,
        is VoiceCommand.OpenSettingsSection,
        VoiceCommand.OpenHelp,
        VoiceCommand.PairDevice,
        VoiceCommand.GoBack,
        VoiceCommand.ToggleHighContrast,
        is VoiceCommand.SetTextScale,
        // The account, and the "Are you sure?" dialogs that guard it: never
        // from a phone nobody is looking at, which may be locked.
        VoiceCommand.GoToLogin,
        VoiceCommand.GoToSignUp,
        VoiceCommand.GoToForgotPassword,
        VoiceCommand.LogOut,
        VoiceCommand.DeleteAccount,
        VoiceCommand.Confirm,
        VoiceCommand.Cancel -> Handler.ON_SCREEN
    }
}
