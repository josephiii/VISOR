package ucf.visor.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Draws a small, persistent scrollbar along the trailing edge whenever [state]
 * has more content than fits on screen — a quiet hint that a screen scrolls,
 * without competing with the actual VISOR content for attention.
 *
 * NOTE: The position will be +24 pixels off the right most edge.
 */
fun Modifier.scrollIndicator(
    state: ScrollState,
    barColor: Color,
    trackColor: Color = barColor.copy(alpha = 0.15f),
): Modifier = drawWithContent {
    drawContent()
    if (state.maxValue <= 0) return@drawWithContent

    val barWidth = 4.dp.toPx()
    val margin = 3.dp.toPx()
//    val xPosition = size.width - barWidth - margin
    val xPosition = size.width + 30.0f // Reason: added to Columns with padding of 24
    val minThumbHeight = 40.dp.toPx()

    val contentHeight = size.height + state.maxValue
    val thumbHeight = (size.height * size.height / contentHeight)
        .coerceAtLeast(minThumbHeight)
        .coerceAtMost(size.height)
    val scrollFraction = state.value.toFloat() / state.maxValue.toFloat()
    val thumbTop = (size.height - thumbHeight) * scrollFraction

    drawRect(
        color = trackColor,
        topLeft = Offset(xPosition, 0f),
        size = Size(barWidth, size.height),
    )
    drawRect(
        color = barColor,
        topLeft = Offset(xPosition, thumbTop),
        size = Size(barWidth, thumbHeight),
    )
}
