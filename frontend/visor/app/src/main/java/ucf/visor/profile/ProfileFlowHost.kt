package ucf.visor.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

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
    speak: (String) -> Unit = {},
    onSetupComplete: () -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { ProfileStore(context) }

    ProfileCreationScreen(
        speak = speak,
        onFinished = { profile ->
            store.save(profile)
            onSetupComplete()
        },
    )
}
