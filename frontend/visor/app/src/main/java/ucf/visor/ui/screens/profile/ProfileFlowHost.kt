package ucf.visor.ui.screens.profile

import androidx.compose.runtime.Composable
import ucf.visor.data.UserProfile
import ucf.visor.data.VoiceCategory
import ucf.visor.ui.viewmodel.VisorViewModel
import ucf.visor.ui.voice.VoiceNavigationController

/**
 * Hosts the profile creation wizard: the "onboarding" route, which a new
 * account always goes through after signing up, and a login goes through when
 * this phone has no profile yet or a sign-up's setup was left unfinished (see
 * VisorViewModel.onSignUpSuccess and ProfileStore.needsSetup).
 *
 * @param speak wired to tts.Speaker via MainActivity -> VisorLayout -> VisorNavHost.
 * @param voiceNav lets the wizard capture a spoken answer for each question
 *   (voice-first by design — see ProfileCreationScreen) using the same
 *   SpeechRecognizer session voice navigation uses elsewhere.
 * @param onSetupComplete Start using VISOR, with the answers: saves them and
 *   goes Home (VisorViewModel.finishSetup).
 */
@Composable
fun ProfileFlowHost(
    viewModel: VisorViewModel,
    speak: (String, VoiceCategory) -> Unit = { _, _ -> },
    voiceNav: VoiceNavigationController? = null,
    onSetupComplete: (UserProfile) -> Unit = {},
) {
    ProfileCreationScreen(
        viewModel = viewModel,
        speak = speak,
        voiceNav = voiceNav,
        onFinished = onSetupComplete,
    )
}
