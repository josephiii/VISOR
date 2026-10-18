package ucf.visor.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A scrollbar along the trailing edge, whenever [state] has more content than
 * fits: a sign that the screen scrolls, and how far down it is. It is drawn
 * inside the bounds of what it is applied to, in dp, so apply it before the
 * screen's horizontal padding and it sits in that margin:
 *
 * ```
 * Modifier
 *     .fillMaxSize()
 *     .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline)
 *     .padding(horizontal = 24.dp)
 *     .verticalScroll(scrollState)
 *     .padding(vertical = 24.dp)
 * ```
 *
 * The bar is 6dp wide, over a 2dp line the length of the screen that shows how
 * far it could go, both in [color]: the theme's outline, on every screen, which
 * is at least 3:1 against the page (WCAG 1.4.11). It was 4dp, over a 15% tint
 * of itself (about 1.25:1), and drawn 30 px past the edge of what it was
 * applied to, which on some screens and phones was off the screen.
 */
fun Modifier.scrollIndicator(state: ScrollState, color: Color): Modifier = drawWithContent {
    drawContent()
    if (state.maxValue <= 0) return@drawWithContent

    val barWidth = BarWidth.toPx()
    val lineWidth = LineWidth.toPx()
    val inset = EdgeInset.toPx()
    val x = if (layoutDirection == LayoutDirection.Rtl) inset else size.width - inset - barWidth

    val contentHeight = size.height + state.maxValue
    val barHeight = (size.height * size.height / contentHeight)
        .coerceAtLeast(MinBarHeight.toPx())
        .coerceAtMost(size.height)
    val scrolled = state.value.toFloat() / state.maxValue.toFloat()
    val barTop = (size.height - barHeight) * scrolled

    drawRect(
        color = color,
        topLeft = Offset(x + (barWidth - lineWidth) / 2, 0f),
        size = Size(lineWidth, size.height),
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(x, barTop),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(barWidth / 2),
    )
}

private val BarWidth = 6.dp
private val LineWidth = 2.dp
private val EdgeInset = 6.dp
private val MinBarHeight = 48.dp
