package ucf.visor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ucf.visor.R

/**
 * Where the user is in a run of steps, such as setup: "Step 2 of 8" in words,
 * over a bar of one segment per step, solid up to this step and hollow after
 * it. Like [VisorSlider]'s track, it shows progress by shape as well as color,
 * where Material's progress bar used a pale tint (WCAG 1.4.11). A screen
 * reader reads it as just "Step 2 of 8".
 *
 * @param step this step, counted from 1.
 * @param of how many steps there are.
 */
@Composable
fun StepIndicator(step: Int, of: Int, modifier: Modifier = Modifier) {
    val filled = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.step_counter, step, of),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(SegmentHeight),
        ) {
            val gap = SegmentGap.toPx()
            val border = SegmentBorder.toPx()
            val width = (size.width - gap * (of - 1)) / of
            val round = CornerRadius(size.height / 2)
            for (i in 0 until of) {
                val position = if (rtl) of - 1 - i else i
                val left = position * (width + gap)
                if (i < step) {
                    drawRoundRect(
                        color = filled,
                        topLeft = Offset(left, 0f),
                        size = Size(width, size.height),
                        cornerRadius = round,
                    )
                } else {
                    drawRoundRect(
                        color = outline,
                        topLeft = Offset(left + border / 2, border / 2),
                        size = Size(width - border, size.height - border),
                        cornerRadius = CornerRadius(size.height / 2 - border / 2),
                        style = Stroke(width = border),
                    )
                }
            }
        }
    }
}

private val SegmentHeight = 14.dp
private val SegmentGap = 6.dp
private val SegmentBorder = 2.dp
