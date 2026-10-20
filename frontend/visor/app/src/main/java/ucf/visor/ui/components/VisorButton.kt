package ucf.visor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ucf.visor.R
import ucf.visor.ui.feedback.LocalHaptics
import ucf.visor.ui.feedback.PressedBorderWidth
import ucf.visor.ui.feedback.controlFeedback
import ucf.visor.ui.feedback.controlState
import ucf.visor.ui.theme.VisorShapes
import ucf.visor.ui.theme.VisorTheme

/**
 * Basic Button implementation. Enforces a touch target well above the
 * ~48dp minimum recommended for low-vision users (Material's own Button
 * default is only 40dp tall). Its label wraps onto more lines rather than
 * shrinking or being cut off, and the button grows to fit it (WCAG 1.4.4).
 *
 * Pressed, it shrinks a little, its colors swap and a thick outline appears,
 * and the phone gives a light tick (see ui/feedback). It shows a ring while it
 * has keyboard or switch-access focus.
 *
 * @param locked shows the button as there but not usable yet: a lock and a
 *   dashed outline instead of the fill, with text still at full contrast (see
 *   [dashedBorder]). It still takes taps, so [onClick] can say what unlocks it,
 *   and the phone buzzes three times rather than ticking.
 * @param busy shows the button working on what it was pressed for, such as
 *   logging in: a spinner beside [text] (which should say so, as in "Logging
 *   in…"), and no response to more presses. It keeps its colors rather than
 *   fading, so what it says stays readable.
 */
@Composable
fun VisorButton(
    text: String,
    width: Float = 0.75f,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    busy: Boolean = false,
) {
    val lockedDescription = stringResource(R.string.state_locked)
    val haptics = LocalHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    val state = interactionSource.controlState()

    val colors = MaterialTheme.colorScheme
    val container = if (locked) Color.Transparent else colors.primary
    val content = if (locked) colors.onSurfaceVariant else colors.onPrimary
    // Pressed, the fill and the text swap colors.
    val pressedContainer = if (locked) colors.onSurfaceVariant else colors.onPrimary
    val pressedContent = if (locked) colors.background else colors.primary

    Button(
        onClick = {
            if (locked) haptics?.rejected() else haptics?.tick()
            onClick()
        },
        enabled = !busy,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth(fraction = width)
            .heightIn(min = 56.dp)
            .controlFeedback(state, VisorShapes.Control, VisorTheme.focusRing)
            .then(
                if (locked) {
                    Modifier
                        .dashedBorder(2.dp, colors.outline, VisorShapes.Control)
                        .semantics { stateDescription = lockedDescription }
                } else {
                    Modifier
                }
            ),
        shape = VisorShapes.Control,
        border = if (state.pressed) BorderStroke(PressedBorderWidth, if (locked) colors.outline else colors.primary) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (state.pressed) pressedContainer else container,
            contentColor = if (state.pressed) pressedContent else content,
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
    ) {
        if (busy) {
            CircularProgressIndicator(
                color = LocalContentColor.current,
                strokeWidth = 3.dp,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(iconSizeFor(MaterialTheme.typography.labelLarge)),
            )
        }
        if (locked) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(iconSizeFor(MaterialTheme.typography.labelLarge)),
            )
        }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Preview
@Composable
fun PreviewVisorButton() {
    VisorButton(
        text = "Preview",
        onClick = {}
    )
}
