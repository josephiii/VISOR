package ucf.visor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ucf.visor.ui.feedback.LocalHaptics
import ucf.visor.ui.feedback.PressedBorderWidth
import ucf.visor.ui.feedback.controlFeedback
import ucf.visor.ui.feedback.controlState
import ucf.visor.ui.theme.VisorShapes
import ucf.visor.ui.theme.VisorTheme
import kotlin.math.roundToInt

/**
 * VISOR's slider, used wherever a value is set along a range. Material's own
 * shows how full it is with two tints too close to tell apart (as little as
 * 1.2:1), and has a thin line for a thumb. This one (WCAG 1.4.11):
 *
 *  - draws the part still to fill hollow, an outline with the background
 *    showing through, and the filled part solid, so how full it is shows by
 *    shape as well as by color, in every theme;
 *  - has a large round thumb, ringed in the background color so it stands
 *    apart from the filled track it sits on;
 *  - marks each notch it can stop at, on the track itself;
 *  - has − and + buttons at either end, which move it a notch at a time, so a
 *    value can be set without finding or dragging the thumb.
 *
 * @param label what the slider sets, such as "Speech rate", for a screen reader.
 * @param valueDescription the value in words, such as "80 percent". A screen
 *   reader says it as the slider's value, and as the − and + buttons' state,
 *   so it is said again after each press.
 * @param decreaseDescription and [increaseDescription] name the − and +
 *   buttons, such as "Talk slower".
 * @param onValueChangeFinished runs once a drag or a tap on the track ends,
 *   and after each press of − or +.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisorSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    label: String,
    valueDescription: String,
    decreaseDescription: String,
    increaseDescription: String,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val sliderInteraction = remember { MutableInteractionSource() }
    val sliderState = sliderInteraction.controlState()
    val notch = (valueRange.endInclusive - valueRange.start) / (steps + 1)
    val atStart = value <= valueRange.start + notch / 2
    val atEnd = value >= valueRange.endInclusive - notch / 2

    fun nudge(notches: Int) {
        // Counted in whole notches from the start, so repeated presses never
        // drift off the notches the slider itself stops at.
        val from = ((value - valueRange.start) / notch).roundToInt()
        val next = (valueRange.start + (from + notches) * notch)
            .coerceIn(valueRange.start, valueRange.endInclusive)
        if (next != value) {
            onValueChange(next)
            onValueChangeFinished?.invoke()
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NudgeButton(
            icon = Icons.Filled.Remove,
            description = decreaseDescription,
            valueDescription = valueDescription,
            atLimit = atStart,
            onClick = { nudge(-1) },
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = sliderInteraction,
            thumb = { Thumb(focused = sliderState.focused) },
            track = { state -> Track(state) },
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = label
                    stateDescription = valueDescription
                },
        )
        NudgeButton(
            icon = Icons.Filled.Add,
            description = increaseDescription,
            valueDescription = valueDescription,
            atLimit = atEnd,
            onClick = { nudge(1) },
        )
    }
}

/**
 * The − or + at one end of the slider. At the end it moves toward, it can't
 * be pressed and is drawn with a dashed outline, as a locked control is,
 * rather than faded: it stays readable.
 */
@Composable
private fun NudgeButton(
    icon: ImageVector,
    description: String,
    valueDescription: String,
    atLimit: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    val state = interactionSource.controlState()
    OutlinedButton(
        onClick = {
            haptics?.tick()
            onClick()
        },
        enabled = !atLimit,
        interactionSource = interactionSource,
        shape = VisorShapes.Control,
        border = when {
            atLimit -> null
            state.pressed -> BorderStroke(PressedBorderWidth, colors.outline)
            else -> BorderStroke(2.dp, colors.outline)
        },
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            // Pressed, it fills with the icon's color, and the icon takes the page's.
            containerColor = if (state.pressed) colors.onSurface else Color.Transparent,
            contentColor = if (state.pressed) colors.background else colors.onSurface,
            disabledContentColor = colors.onSurface,
        ),
        modifier = Modifier
            .size(NudgeButtonSize)
            .controlFeedback(state, VisorShapes.Control, VisorTheme.focusRing)
            .then(if (atLimit) Modifier.dashedBorder(2.dp, colors.outline, VisorShapes.Control) else Modifier)
            .semantics {
                contentDescription = description
                stateDescription = valueDescription
            },
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(32.dp))
    }
}

/**
 * The track: hollow where the slider has still to fill, solid where it has
 * filled, with a dot at each notch. It reaches a little past the first and
 * last notches, so those sit inside its rounded ends.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Track(state: SliderState) {
    val filledColor = MaterialTheme.colorScheme.primary
    val notchOnFilled = MaterialTheme.colorScheme.onPrimary
    val outline = MaterialTheme.colorScheme.outline
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(TrackHeight),
    ) {
        val range = state.valueRange
        val span = range.endInclusive - range.start
        val fraction = if (span == 0f) 0f else ((state.value - range.start) / span).coerceIn(0f, 1f)
        // Drawn past the canvas's ends, into the room the slider keeps there for its thumb.
        val overhang = size.height / 2
        val border = TrackBorder.toPx()
        val round = CornerRadius(size.height / 2)

        drawRoundRect(
            color = outline,
            topLeft = Offset(-overhang + border / 2, border / 2),
            size = Size(size.width + overhang * 2 - border, size.height - border),
            cornerRadius = CornerRadius(size.height / 2 - border / 2),
            style = Stroke(width = border),
        )
        val filledEnd = size.width * fraction
        val filledStart = -overhang
        val filledWidth = filledEnd - filledStart
        drawRoundRect(
            color = filledColor,
            topLeft = Offset(if (rtl) size.width - filledEnd else filledStart, 0f),
            size = Size(filledWidth, size.height),
            cornerRadius = round,
        )

        val notches = state.steps + 2
        for (i in 0 until notches) {
            val at = i / (notches - 1f)
            val x = size.width * (if (rtl) 1f - at else at)
            drawCircle(
                color = if (at <= fraction) notchOnFilled else outline,
                radius = NotchRadius.toPx(),
                center = Offset(x, size.height / 2),
            )
        }
    }
}

/**
 * The thumb: a large primary disc with a dot at its center, inside a ring of
 * the background color that parts it from the filled track it sits on. While
 * the slider has keyboard or switch-access focus, the focus ring circles it.
 */
@Composable
private fun Thumb(focused: Boolean) {
    val ring = MaterialTheme.colorScheme.background
    val body = MaterialTheme.colorScheme.primary
    val dot = MaterialTheme.colorScheme.onPrimary
    val focusRing = VisorTheme.focusRing
    Canvas(modifier = Modifier.size(ThumbSize)) {
        val radius = size.minDimension / 2
        if (focused) {
            drawCircle(
                color = focusRing,
                radius = radius + ThumbFocusGap.toPx(),
                style = Stroke(width = ThumbFocusWidth.toPx()),
            )
        }
        drawCircle(color = ring, radius = radius)
        drawCircle(color = body, radius = radius - ThumbRing.toPx())
        drawCircle(color = dot, radius = ThumbDot.toPx())
    }
}

private val NudgeButtonSize = 56.dp
private val TrackHeight = 14.dp
private val TrackBorder = 2.dp
private val NotchRadius = 3.dp
private val ThumbSize = 34.dp
private val ThumbRing = 4.dp
private val ThumbDot = 5.dp
private val ThumbFocusGap = 3.dp
private val ThumbFocusWidth = 3.dp
