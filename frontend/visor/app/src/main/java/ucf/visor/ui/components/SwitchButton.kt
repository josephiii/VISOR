package ucf.visor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ucf.visor.ui.feedback.LocalHaptics
import ucf.visor.ui.feedback.PressedBorderWidth
import ucf.visor.ui.feedback.controlFeedback
import ucf.visor.ui.feedback.controlState
import ucf.visor.ui.theme.VisorShapes
import ucf.visor.ui.theme.VisorTheme

/**
 * A full-width action button. Its label wraps rather than shrinking or being
 * cut off, and the button grows to fit it (WCAG 1.4.4). Pressed and focused,
 * it looks and feels like [VisorButton].
 *
 * @param isDestructive for actions that are hard to undo, such as logging out
 *   or deleting an account: drawn in the error colors.
 * @param outlined for the quieter of two choices, such as Cancel beside Log
 *   out: an outline around text in the page's own colors.
 * @param icon drawn before the label, growing with it.
 * @param textStyle and [minHeight] for a button that should stand out more,
 *   such as setup's Continue.
 * @param enabled false while the action can't be taken. It is then drawn with
 *   a dashed outline, as a locked control is, rather than faded, so its label
 *   stays readable.
 */
@Composable
fun SwitchButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    outlined: Boolean = false,
    icon: ImageVector? = null,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    minHeight: Dp = 56.dp,
    enabled: Boolean = true,
) {
    val haptics = LocalHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    val state = interactionSource.controlState()

    val colors = MaterialTheme.colorScheme
    val container = when {
        outlined -> colors.background
        isDestructive -> colors.errorContainer
        else -> colors.primary
    }
    val content = when {
        outlined -> colors.onSurface
        isDestructive -> colors.onErrorContainer
        else -> colors.onPrimary
    }
    val style = textStyle

    Button(
        modifier = modifier
            // A minimum, not a fixed height: at large text sizes the label
            // needs more than 56dp, and a fixed height clipped it.
            .heightIn(min = minHeight)
            .fillMaxWidth()
            .controlFeedback(state, VisorShapes.Control, VisorTheme.focusRing)
            .then(
                if (enabled) Modifier
                else Modifier.dashedBorder(2.dp, colors.outline, VisorShapes.Control)
            ),
        onClick = {
            haptics?.tick()
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        shape = VisorShapes.Control,
        // Pressed, the fill and the text swap colors inside a thick outline.
        border = when {
            state.pressed -> BorderStroke(PressedBorderWidth, if (outlined) colors.outline else container)
            outlined && enabled -> BorderStroke(2.dp, colors.outline)
            else -> null
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (state.pressed) content else container,
            contentColor = if (state.pressed) container else content,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = colors.onSurfaceVariant,
        ),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(iconSizeFor(style)),
            )
        }
        Text(label, style = style, textAlign = TextAlign.Center)
    }
}

@Preview
@Composable
fun SwitchButtonPreview() {
    SwitchButton(
        onClick = {},
        label = "Test Label"
    )
}
