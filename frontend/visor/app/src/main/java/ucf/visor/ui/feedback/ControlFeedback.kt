package ucf.visor.ui.feedback

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Whether a control is being pressed, and whether it has keyboard,
 * switch-access or D-pad focus. VISOR's buttons and chips share one look for
 * each (see [controlFeedback]): Material's own is a faint ripple, there for
 * about 300 ms, and a focus tint that is hard to find.
 */
@Immutable
data class ControlState(val pressed: Boolean, val focused: Boolean)

@Composable
fun InteractionSource.controlState(): ControlState {
    val pressed by collectIsPressedAsState()
    val focused by collectIsFocusedAsState()
    return ControlState(pressed = pressed, focused = focused)
}

/**
 * The parts of the pressed and focused looks that are the same for every
 * control:
 *
 *  - pressed, it shrinks a little (to 97%), held for as long as the press,
 *    so it shows under a finger and through blur alike. Each control also
 *    swaps its fill and text colors and thickens its outline to 3dp;
 *  - focused, a 3dp ring in the theme's focus color (VisorTheme.focusRing)
 *    is drawn just outside [shape], with a gap so it never merges into the
 *    control's own border (WCAG 2.4.7).
 *
 * Neither animates, so both show the same with the system's animations off.
 */
fun Modifier.controlFeedback(state: ControlState, shape: Shape, focusColor: Color): Modifier =
    this
        .then(if (state.focused) Modifier.focusRing(shape, focusColor) else Modifier)
        .then(
            if (state.pressed) {
                Modifier.graphicsLayer {
                    scaleX = PressedScale
                    scaleY = PressedScale
                }
            } else {
                Modifier
            }
        )

/** A ring of [color] just outside [shape]. */
private fun Modifier.focusRing(shape: Shape, color: Color): Modifier = drawWithContent {
    drawContent()
    val stroke = FocusRingWidth.toPx()
    // From the control's edge out: a gap, then the ring.
    val inset = FocusRingGap.toPx() + stroke / 2
    val outline = shape.createOutline(
        Size(size.width + inset * 2, size.height + inset * 2),
        layoutDirection,
        this,
    )
    translate(-inset, -inset) {
        drawOutline(outline, color, style = Stroke(width = stroke))
    }
}

/** How big a pressed control is drawn. */
const val PressedScale = 0.97f

/** The outline of a pressed control. */
val PressedBorderWidth = 3.dp

private val FocusRingWidth = 3.dp
private val FocusRingGap = 3.dp
