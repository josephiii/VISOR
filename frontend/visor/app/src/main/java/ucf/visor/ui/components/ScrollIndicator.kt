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
 * Apply this BEFORE `.verticalScroll(state)` in the modifier chain — e.g.
 * `Modifier.fillMaxSize().scrollIndicator(state, color).verticalScroll(state).padding(24.dp)`.
 * That ordering matters more than it looks: `verticalScroll` measures
 * whatever comes AFTER it with an unbounded max height (so its content can be
 * taller than the screen) and then scrolls that whole subtree by an offset.
 * A modifier chained after `verticalScroll` is *inside* that subtree — its
 * [size] is the full (unbounded) content height, not the viewport, and its
 * drawing pans away with the rest of the content instead of staying put.
 * That's exactly what produced "one long solid bar with no visible position":
 * the thumb was sized against the full content height (so it came out nearly
 * as tall as the whole bar) and then scrolled along with everything else, so
 * only a near-solid-colored slice of it was ever visible in the viewport.
 * Chained BEFORE `verticalScroll` instead, this modifier sees the true,
 * fixed viewport size and draws as a real overlay that doesn't move with the
 * content — only the thumb's position within it changes as [state] changes.
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
    val xPosition = size.width + 24.0f // Reason: added to Columns with padding of 24
    val minThumbHeight = 40.dp.toPx()

    val contentHeight = size.height + state.maxValue
    val thumbHeight = (size.height * size.height / contentHeight)
        .coerceAtLeast(minThumbHeight)
        .coerceAtMost(size.height)
    val scrollFraction = state.value.toFloat() / state.maxValue.toFloat()
    val thumbTop = (size.height - thumbHeight) * scrollFraction

    // Faint full-length track: without it, a small moving thumb has nothing to
    // read its position against, which is exactly the "can't see progress"
    // problem — but kept low-alpha so it doesn't add visual noise at rest.
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
