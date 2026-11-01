package ucf.visor.ui.viewmodel

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import ucf.visor.R

/**
 * What Home's capture button (and the glasses' copy of it) does with its
 * picture. The camera wake phrases each have their own feature and don't
 * depend on it.
 */
enum class CaptureMode { HAZARD, SCENE, READER, IDENTIFY }

/** The mode's name, as Home's mode choices show it. */
@get:StringRes
val CaptureMode.labelRes: Int
    get() = when (this) {
        CaptureMode.HAZARD -> R.string.hazard_mode
        CaptureMode.SCENE -> R.string.scene_mode
        CaptureMode.READER -> R.string.reader_mode
        CaptureMode.IDENTIFY -> R.string.identify_mode
    }

/**
 * The mode's icon, beside its name on Home. Never the only sign of the mode:
 * a warning sign for hazards, a landscape for a scene, a book for reading,
 * shapes for identifying objects.
 */
val CaptureMode.icon: ImageVector
    get() = when (this) {
        CaptureMode.HAZARD -> Icons.Filled.Warning
        CaptureMode.SCENE -> Icons.Filled.Landscape
        CaptureMode.READER -> Icons.AutoMirrored.Filled.MenuBook
        CaptureMode.IDENTIFY -> Icons.Filled.Category
    }
