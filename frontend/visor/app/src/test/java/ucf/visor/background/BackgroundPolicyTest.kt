package ucf.visor.background

import org.junit.Assert.assertEquals
import org.junit.Test
import ucf.visor.background.BackgroundPolicy.Handler.ANYWHERE
import ucf.visor.background.BackgroundPolicy.Handler.ON_SCREEN
import ucf.visor.data.CameraChoice
import ucf.visor.data.TextScale
import ucf.visor.data.Verbosity
import ucf.visor.stt.CameraCommand
import ucf.visor.ui.viewmodel.SettingsSection
import ucf.visor.ui.voice.VoiceCommand
import ucf.visor.ui.voice.VoiceCommandParser

/**
 * What works with the screen off (background listening). Pictures, answers
 * and how VISOR sounds work anywhere; screens and the account never do.
 */
class BackgroundPolicyTest {

    @Test
    fun picturesAndAnswersWorkWithTheScreenOff() {
        CameraCommand.entries.forEach { assertEquals(it.name, ANYWHERE, handler(VoiceCommand.RunCamera(it))) }
        listOf(
            VoiceCommand.RepeatLast,
            VoiceCommand.StopReading,
            VoiceCommand.PauseReading,
            VoiceCommand.ResumeReading,
        ).forEach { assertEquals(it.toString(), ANYWHERE, handler(it)) }
    }

    @Test
    fun howVisorSoundsCanChangeWithTheScreenOff() {
        listOf(
            VoiceCommand.UseCamera(CameraChoice.GLASSES),
            VoiceCommand.UseCamera(CameraChoice.PHONE),
            VoiceCommand.SetSpeechRate(1.0f),
            VoiceCommand.SpeakFaster,
            VoiceCommand.SpeakSlower,
            VoiceCommand.NextVoice,
            VoiceCommand.PreviousVoice,
            VoiceCommand.DefaultVoice,
            VoiceCommand.SetVerbosity(Verbosity.BRIEF),
        ).forEach { assertEquals(it.toString(), ANYWHERE, handler(it)) }
    }

    /** Nobody should have to find the phone to silence VISOR. */
    @Test
    fun backgroundListeningCanBeStoppedFromAPocket() {
        assertEquals(ANYWHERE, handler(VoiceCommand.SetHazardWatch(on = true)))
        assertEquals(ANYWHERE, handler(VoiceCommand.StopListening))
        assertEquals(ANYWHERE, handler(VoiceCommand.KeepListening))
    }

    @Test
    fun screensNeedTheScreen() {
        listOf(
            VoiceCommand.GoHome,
            VoiceCommand.OpenSettings,
            VoiceCommand.OpenSettingsSection(SettingsSection.SOUND),
            VoiceCommand.OpenHelp,
            VoiceCommand.PairDevice,
            VoiceCommand.GoBack,
            VoiceCommand.ToggleHighContrast,
            VoiceCommand.UseCustomColors,
            VoiceCommand.UseVisorColors,
            VoiceCommand.SetTextScale(TextScale.LARGE),
        ).forEach { assertEquals(it.toString(), ON_SCREEN, handler(it)) }
    }

    /** No account changes, or "Are you sure?" answers, from a phone nobody is looking at. */
    @Test
    fun theAccountNeedsTheScreen() {
        listOf(
            VoiceCommand.LogOut,
            VoiceCommand.DeleteAccount,
            VoiceCommand.Confirm,
            VoiceCommand.Cancel,
            VoiceCommand.GoToLogin,
            VoiceCommand.GoToSignUp,
            VoiceCommand.GoToForgotPassword,
        ).forEach { assertEquals(it.toString(), ON_SCREEN, handler(it)) }
    }

    /** Every phrase Settings suggests has an answer either way, said aloud. */
    @Test
    fun saidCommandsAllHaveAHandler() {
        listOf("delete my account", "yes", "repeat that", "stop listening", "keep listening", "faster")
            .map(VoiceCommandParser::parse)
            .forEach { BackgroundPolicy.handler(it) }
    }

    @Test
    fun theReplyWithNoScreenSaysWhatToDo() {
        assertEquals("Open VISOR on your phone to do that.", BackgroundPolicy.NEEDS_SCREEN)
    }

    private fun handler(command: VoiceCommand) = BackgroundPolicy.handler(command)
}
