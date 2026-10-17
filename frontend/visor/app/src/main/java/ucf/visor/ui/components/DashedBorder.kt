package ucf.visor.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A dashed outline in [shape]: how VISOR draws a control that is on screen
 * but locked (see `locked` on [VisorButton] and [SelectableChip]).
 *
 * The dashes say "not yet" through shape rather than by fading the control.
 * A faded, low-opacity control drops below the contrast low-vision users need
 * to read it, and would hide the very thing it is there to show them.
 */
fun Modifier.dashedBorder(width: Dp, color: Color, shape: Shape): Modifier = drawWithCache {
    val strokeWidth = width.toPx()
    // Inset by half the stroke, so the dashes sit where a solid border would.
    val outline = shape.createOutline(
        Size(size.width - strokeWidth, size.height - strokeWidth),
        layoutDirection,
        this,
    )
    val dashes = PathEffect.dashPathEffect(floatArrayOf(DashLength.toPx(), DashGap.toPx()))
    onDrawWithContent {
        drawContent()
        translate(strokeWidth / 2, strokeWidth / 2) {
            drawOutline(outline, color, style = Stroke(width = strokeWidth, pathEffect = dashes))
        }
    }
}

// Long dashes and wide gaps, so the outline still reads as dashed through blur.
private val DashLength = 10.dp
private val DashGap = 6.dp
