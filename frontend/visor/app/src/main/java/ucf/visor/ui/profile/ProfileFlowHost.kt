package ucf.visor.ui.profile

import androidx.compose.runtime.Composable
import ucf.visor.ui.screens.profile.ProfileCreationScreen
import ucf.visor.ui.viewmodel.VisorViewModel

/**
 * VISOR-124 — hosts the profile creation wizard and persists the result.
 *
 * This is the piece navigation will point at for first-run:
 *   startDestination = if (ProfileStore(context).hasProfile()) "home" else "profileCreation"
 *
 * @param speak wire to tts.Speaker once OCR_TTS_Integration merges:
 *   ProfileFlowHost(speak = { speaker.speak(it) })
 * @param onSetupComplete navigate to Home (and clear backstack) when nav exists.
 */
@Composable
fun ProfileFlowHost(
    viewModel: VisorViewModel,
    speak: (String) -> Unit = {},
    onSetupComplete: () -> Unit = {},
) {
    ProfileCreationScreen(
        viewModel = viewModel,
        speak = speak,
        onFinished = { profile ->
            viewModel.updateProfile(profile)
            onSetupComplete()
        },
    )
}
