package ucf.visor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ucf.visor.R
import ucf.visor.ui.theme.VisorShapes

/**
 * VISOR's single selectable-choice control — used for settings toggles,
 * onboarding answers, and mode pickers.
 * @param minHeight touch-target height. Defaults to 64.dp (well above the
 *   ~48dp minimum).
 * @param showCheckmark set false for chips packed 3-wide in a Row, where a
 *   checkmark would fight the label for space at large text sizes — the
 *   container color and bold weight still carry the selected state.
 * @param locked shows the chip as there but not choosable yet: a dashed
 *   outline instead of the fill, text still at full contrast (see
 *   [dashedBorder]). It still takes taps, so [onClick] can say what unlocks it.
 */
@Composable
fun SelectableChip(
    label: String,
    selected: Boolean,
    vital: Boolean = false,
    modifier: Modifier = Modifier,
    minHeight: Dp = 64.dp,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    showCheckmark: Boolean = true,
    locked: Boolean = false,
    onClick: () -> Unit,
) {
    val containerColor =
        if (locked) {
            Color.Transparent
        } else if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else if (vital) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    val contentColor =
        if (locked) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else if (vital) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val borderColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else if (vital) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.outline
        }
    // Compose only says "selected" when no other state description is set, so
    // a locked chip says both itself.
    val lockedDescription = stringResource(
        if (selected) R.string.state_selected_locked else R.string.state_not_selected_locked
    )

    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = minHeight)
            .semantics {
                this.selected = selected
                if (locked) stateDescription = lockedDescription
            }
            .then(
                if (locked) Modifier.dashedBorder(2.dp, borderColor, VisorShapes.Control)
                else Modifier
            ),
        shape = VisorShapes.Control,
        border = if (locked) null else BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Weighted so it's measured against the space actually left after the
            // checkmark, not the full row — otherwise AutoSizeText would decide it
            // already fits (against the full width) and stop shrinking too early,
            // overlapping the icon.
            AutoSizeText(
                text = label,
                style = textStyle,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (selected && showCheckmark) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.width(24.dp),
                )
            }
        }
    }
}
