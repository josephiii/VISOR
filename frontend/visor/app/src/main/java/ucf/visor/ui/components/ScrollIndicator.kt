package ucf.visor.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Draws a persistent, high-contrast scrollbar along the trailing edge whenever
 * [state] has more content than fits on screen.
 *
 * Nothing on VISOR's scrollable screens (Login, Settings, onboarding, ...)
 * previously hinted that there was more content below — content simply ended at
 * the bottom of the viewport. That's easy to miss even at standard text size, and
 * VISOR now defaults every new profile to Extra Large text, which makes it far
 * more likely a screen needs scrolling that didn't before. The platform's default
 * scrollbar only flashes briefly during a scroll gesture and is deliberately
 * subtle; this one stays visible the whole time content overflows, at a size and
 * contrast built for low vision rather than for looking unobtrusive.
 *
 * Apply after `.verticalScroll(state)` so it draws over the final viewport rather
 * than scrolling away with the content.
 */
fun Modifier.scrollIndicator(
    state: ScrollState,
    color: Color,
    trackColor: Color = color.copy(alpha = 0.25f),
): Modifier = drawWithContent {
    drawContent()
    if (state.maxValue <= 0) return@drawWithContent

    val barWidth = 6.dp.toPx()
    val margin = 3.dp.toPx()
    val trackLeft = size.width - barWidth - margin
    val minThumbHeight = 40.dp.toPx()

    val contentHeight = size.height + state.maxValue
    val thumbHeight = (size.height * size.height / contentHeight).coerceAtLeast(minThumbHeight)
        .coerceAtMost(size.height)
    val scrollFraction = state.value.toFloat() / state.maxValue.toFloat()
    val thumbTop = (size.height - thumbHeight) * scrollFraction

    drawRoundRect(
        color = trackColor,
        topLeft = Offset(trackLeft, 0f),
        size = Size(barWidth, size.height),
        cornerRadius = CornerRadius(barWidth / 2),
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(trackLeft, thumbTop),
        size = Size(barWidth, thumbHeight),
        cornerRadius = CornerRadius(barWidth / 2),
    )
}
