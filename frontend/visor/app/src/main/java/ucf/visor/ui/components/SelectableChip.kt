package ucf.visor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ucf.visor.ui.theme.VisorShapes

/**
 * VISOR's single selectable-choice control — used for settings toggles,
 * onboarding answers, and mode pickers.
 *
 * Consolidates three near-duplicate components that had drifted apart:
 * SettingsScreen's private ChoiceChip took a `selected` flag but never
 * changed color for it (users couldn't tell which speech rate/verbosity was
 * active), while Phase1ModeConfigurationScreen's private ChoiceChip and
 * ProfileCreationScreen's BigChoiceButton each independently got selection
 * styling right in slightly different ways. One component now owns it: a
 * container-color swap (always from the current theme, so it's correct in
 * all four AppThemes) plus an optional checkmark, exposed via
 * `Modifier.semantics { selected }` for screen readers.
 *
 * @param minHeight touch-target height. Defaults to 64.dp (well above the
 *   ~48dp minimum); onboarding's BigChoiceButton uses a taller 72.dp.
 * @param showCheckmark set false for chips packed 3-wide in a Row, where a
 *   checkmark would fight the label for space at large text sizes — the
 *   container color and bold weight still carry the selected state.
 */
@Composable
fun SelectableChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    minHeight: Dp = 64.dp,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    showCheckmark: Boolean = true,
    onClick: () -> Unit,
) {
    val containerColor =
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val contentColor =
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val borderColor =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = minHeight)
            .semantics { this.selected = selected },
        shape = VisorShapes.Control,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = textStyle,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
            if (selected && showCheckmark) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = contentColor,
                )
            }
        }
    }
}
