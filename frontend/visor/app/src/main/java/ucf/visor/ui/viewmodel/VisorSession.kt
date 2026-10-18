package ucf.visor.ui.viewmodel

import androidx.annotation.StringRes
import ucf.visor.R

enum class SessionMode { HAZARD, SCENE, READER }

/** The mode's name, as Home's mode choices and the session status bar show it. */
@get:StringRes
val SessionMode.labelRes: Int
    get() = when (this) {
        SessionMode.HAZARD -> R.string.hazard_mode
        SessionMode.SCENE -> R.string.scene_mode
        SessionMode.READER -> R.string.reader_mode
    }

/** What [VisorViewModel.toggleSession] did, for the caller to announce. */
sealed interface SessionChange {
    data object Started : SessionChange
    data object Ended : SessionChange

    /** The session could not start: [problem] says why, in words fit to speak. */
    data class NotStarted(val problem: String) : SessionChange
}

data class VisorSession(
    val inHazardAwarenessMode: Boolean = true, // Default.
    val inSceneDescriptionMode: Boolean = false,
    val inReadingAssistanceMode: Boolean = false,
) {
    val mode: SessionMode = when {
        inHazardAwarenessMode -> SessionMode.HAZARD
        inSceneDescriptionMode -> SessionMode.SCENE
        else -> SessionMode.READER
    }
}