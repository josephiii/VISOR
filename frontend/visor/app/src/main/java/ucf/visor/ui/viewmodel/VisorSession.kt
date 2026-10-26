package ucf.visor.ui.viewmodel

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import ucf.visor.R
import ucf.visor.stt.CameraCommand

enum class SessionMode { HAZARD, SCENE, READER }

/** The mode's name, as Home's mode choices and the session status bar show it. */
@get:StringRes
val SessionMode.labelRes: Int
    get() = when (this) {
        SessionMode.HAZARD -> R.string.hazard_mode
        SessionMode.SCENE -> R.string.scene_mode
        SessionMode.READER -> R.string.reader_mode
    }

/**
 * The mode's icon, beside its name on Home and in its place in the session
 * status bar when the name doesn't fit there. Always drawn with its name in
 * reach (or, for a screen reader, in place of it), never as the only sign of
 * the mode: a warning sign for hazards, a landscape for a scene, a book for
 * reading.
 */
val SessionMode.icon: ImageVector
    get() = when (this) {
        SessionMode.HAZARD -> Icons.Filled.Warning
        SessionMode.SCENE -> Icons.Filled.Landscape
        SessionMode.READER -> Icons.AutoMirrored.Filled.MenuBook
    }

/**
 * The mode a camera wake phrase belongs to. Saying one switches the session to
 * its mode, so the status bar, Home's mode choices and Home's capture button
 * all follow what the user last asked for: after "VISOR, read this", the
 * capture button reads text too. Naming objects goes with describing the
 * scene, the mode about what is around the wearer (its description falls back
 * to naming objects when the server can't be reached).
 */
val CameraCommand.mode: SessionMode
    get() = when (this) {
        CameraCommand.READ_TEXT -> SessionMode.READER
        CameraCommand.IDENTIFY_OBJECTS -> SessionMode.SCENE
        CameraCommand.DESCRIBE_SCENE -> SessionMode.SCENE
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