package ucf.visor.ui.viewmodel

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import ucf.visor.R

enum class SessionMode { HAZARD, SCENE, READER, QUESTION }

/** The mode's name, as Home's mode choices and the session status bar show it. */
@get:StringRes
val SessionMode.labelRes: Int
    get() = when (this) {
        SessionMode.HAZARD -> R.string.hazard_mode
        SessionMode.SCENE -> R.string.scene_mode
        SessionMode.READER -> R.string.reader_mode
        SessionMode.QUESTION -> R.string.question_mode
    }

/**
 * The mode's icon, beside its name on Home and in its place in the session
 * status bar when the name doesn't fit there. Always drawn with its name in
 * reach (or, for a screen reader, in place of it), never as the only sign of
 * the mode: a warning sign for hazards, a landscape for a scene, a book for
 * reading, a speech bubble for questions.
 */
val SessionMode.icon: ImageVector
    get() = when (this) {
        SessionMode.HAZARD -> Icons.Filled.Warning
        SessionMode.SCENE -> Icons.Filled.Landscape
        SessionMode.READER -> Icons.AutoMirrored.Filled.MenuBook
        SessionMode.QUESTION -> Icons.Filled.QuestionAnswer
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
    val inQuestionMode: Boolean = false,
) {
    val mode: SessionMode = when {
        inHazardAwarenessMode -> SessionMode.HAZARD
        inSceneDescriptionMode -> SessionMode.SCENE
        inQuestionMode -> SessionMode.QUESTION
        else -> SessionMode.READER
    }
}