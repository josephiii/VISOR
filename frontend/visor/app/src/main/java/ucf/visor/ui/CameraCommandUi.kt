package ucf.visor.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import ucf.visor.R
import ucf.visor.stt.CameraCommand

/**
 * The camera feature's name, the same everywhere it is shown: Home's button
 * for it, the Last answer card, Settings' list of wake phrases.
 */
@get:StringRes
val CameraCommand.labelRes: Int
    get() = when (this) {
        CameraCommand.CHECK_HAZARDS -> R.string.camera_command_check_hazards
        CameraCommand.READ_TEXT -> R.string.camera_command_read_text
        CameraCommand.IDENTIFY_OBJECTS -> R.string.camera_command_identify_objects
        CameraCommand.DESCRIBE_SCENE -> R.string.camera_command_describe_scene
        CameraCommand.ASK_QUESTION -> R.string.camera_command_ask_question
    }

/**
 * The feature's icon, beside its name on Home's button. Never the only sign of
 * what a button does: a warning sign for hazards, a book for reading, shapes
 * for identifying objects, a landscape for the scene, a speech bubble for a
 * question.
 */
val CameraCommand.icon: ImageVector
    get() = when (this) {
        CameraCommand.CHECK_HAZARDS -> Icons.Filled.Warning
        CameraCommand.READ_TEXT -> Icons.AutoMirrored.Filled.MenuBook
        CameraCommand.IDENTIFY_OBJECTS -> Icons.Filled.Category
        CameraCommand.DESCRIBE_SCENE -> Icons.Filled.Landscape
        CameraCommand.ASK_QUESTION -> Icons.Filled.QuestionAnswer
    }
