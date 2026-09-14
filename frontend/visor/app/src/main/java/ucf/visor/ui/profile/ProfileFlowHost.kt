package ucf.visor.ui.profile

import androidx.compose.runtime.Composable
import ucf.visor.ui.screens.profile.ProfileCreationScreen
import ucf.visor.ui.viewmodel.VisorViewModel
import ucf.visor.ui.voice.VoiceNavigationController

/**
 * Hosts the profile creation wizard and persists the result.
 *
 * This is the piece navigation will point at for first-run:
 *   startDestination = if (ProfileStore(context).hasProfile()) "home" else "profileCreation"
 *
 * @param speak wired to tts.Speaker via MainActivity -> VisorLayout -> VisorNavHost.
 * @param voiceNav lets the wizard capture a spoken answer for each question
 *   (voice-first by design — see ProfileCreationScreen) using the same
 *   SpeechRecognizer session voice navigation uses elsewhere.
 * @param onSetupComplete navigate to Home (and clear backstack) when nav exists.
 */
@Composable
fun ProfileFlowHost(
    viewModel: VisorViewModel,
    speak: (String) -> Unit = {},
    voiceNav: VoiceNavigationController? = null,
    onSetupComplete: () -> Unit = {},
) {
    ProfileCreationScreen(
        viewModel = viewModel,
        speak = speak,
        voiceNav = voiceNav,
        onFinished = { profile ->
            viewModel.updateProfile(profile)
            onSetupComplete()
        },
    )
}
