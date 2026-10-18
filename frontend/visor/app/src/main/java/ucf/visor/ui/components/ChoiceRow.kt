package ucf.visor.ui.components

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A few choices, such as three [SelectableChip]s, side by side when each fits
 * its share of the width on one line, and stacked at full width when any
 * doesn't. At large text sizes, three chips in a row would otherwise wrap
 * their labels a word to a line, or (as before) shrink them below the size the
 * user chose. Either way they keep their order, left to right or top to bottom.
 *
 * The choice is made from each chip's widest look, so selecting one, which
 * makes its label bold, never flips the layout.
 */
@Composable
fun ChoiceRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 10.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier.selectableGroup()) { measurables, constraints ->
        if (measurables.isEmpty()) return@Layout layout(0, 0) {}
        val gap = spacing.roundToPx()
        val width = constraints.maxWidth
        val slot = (width - gap * (measurables.size - 1)) / measurables.size
        val sideBySide = constraints.hasBoundedWidth &&
            measurables.all { it.maxIntrinsicWidth(Constraints.Infinity) <= slot }

        if (sideBySide) {
            // One height for the row, the tallest chip's, so their edges line up.
            val height = measurables.maxOf { it.minIntrinsicHeight(slot) }
            val placeables = measurables.map {
                it.measure(Constraints(minWidth = slot, maxWidth = slot, minHeight = height, maxHeight = height))
            }
            layout(width, height) {
                placeables.forEachIndexed { index, placeable -> placeable.placeRelative(index * (slot + gap), 0) }
            }
        } else {
            val each = if (constraints.hasBoundedWidth) Constraints.fixedWidth(width) else Constraints()
            val placeables = measurables.map { it.measure(each) }
            val height = placeables.sumOf { it.height } + gap * (placeables.size - 1)
            layout(placeables.maxOf { it.width }, height) {
                var y = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(0, y)
                    y += placeable.height + gap
                }
            }
        }
    }
}
