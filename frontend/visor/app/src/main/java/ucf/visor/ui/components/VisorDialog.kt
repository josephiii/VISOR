package ucf.visor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ucf.visor.R
import ucf.visor.data.VoiceCategory
import ucf.visor.ui.theme.VisorShapes

/**
 * VISOR's dialog: a question to answer before going on, such as "Log out?".
 * Material's put two small text-only buttons side by side, where the action
 * that can't be undone looked just like Cancel. Here:
 *
 *  - the buttons are full width, 56dp tall and stacked, the action above
 *    Cancel, so each is easy to find and hard to hit by mistake;
 *  - with [destructive], the action is filled in the error colors, with an
 *    icon ([confirmIcon]), and Cancel is an outline, so they can't be confused;
 *  - it has VISOR's cut corners and an outline, rather than Material's rounded,
 *    borderless card;
 *  - with [talk], VISOR says the title and message as it opens, for a user who
 *    isn't looking (a screen reader says the title itself, from its pane title).
 *
 * @param dismissLabel null for a dialog with just the one button, such as "Got it".
 */
@Composable
fun VisorDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = stringResource(R.string.cancel),
    destructive: Boolean = false,
    confirmIcon: ImageVector? = null,
    talk: ((String, VoiceCategory) -> Unit)? = null,
) {
    val context = LocalContext.current
    if (talk != null) {
        LaunchedEffect(title) {
            if (!context.isScreenReaderOn()) talk("$title $message", VoiceCategory.VOICE_COMMANDS)
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        // As wide as the screen, less its margins, so the buttons are as wide as they can be.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = VisorShapes.Control,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .semantics { paneTitle = title },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(message, style = MaterialTheme.typography.bodyLarge)
                SwitchButton(
                    label = confirmLabel,
                    onClick = onConfirm,
                    isDestructive = destructive,
                    icon = confirmIcon,
                )
                if (dismissLabel != null) {
                    SwitchButton(label = dismissLabel, onClick = onDismiss, outlined = true)
                }
            }
        }
    }
}
