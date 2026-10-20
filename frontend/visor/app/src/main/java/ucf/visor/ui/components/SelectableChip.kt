package ucf.visor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ucf.visor.R
import ucf.visor.ui.feedback.LocalHaptics
import ucf.visor.ui.feedback.PressedBorderWidth
import ucf.visor.ui.feedback.controlFeedback
import ucf.visor.ui.feedback.controlState
import ucf.visor.ui.theme.VisorShapes
import ucf.visor.ui.theme.VisorTheme

/**
 * VISOR's choice control: one option of a few, such as a session mode or a
 * text size, or with [multiSelect], one of a set that can be picked together,
 * such as the kinds of vision loss. A screen reader announces it as the choice
 * it is, a radio button or a checkbox. An on/off setting is a [SwitchChip],
 * and an action is a [VisorButton] or [SwitchButton], never a chip: a chip
 * that only does something would be announced as an unchecked choice
 * (WCAG 4.1.2).
 *
 * Selected, it is filled, its border thickens, its label turns bold and a
 * checkmark appears, so the state never rests on a tint. The label wraps
 * rather than shrinking or being cut off, and the chip grows to fit it
 * (WCAG 1.4.4). A press looks and feels like one on [VisorButton].
 *
 * @param minHeight touch-target height. Defaults to 64.dp (well above the
 *   ~48dp minimum).
 * @param locked shows the chip as there but not choosable yet: a dashed
 *   outline instead of the fill, text still at full contrast (see
 *   [dashedBorder]). It still takes taps, so [onClick] can say what unlocks it,
 *   and the phone buzzes three times rather than ticking.
 * @param icon drawn before the label, at the label's size, such as a session
 *   mode's icon. It only adds to the label, so a screen reader doesn't read it.
 */
@Composable
fun SelectableChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    multiSelect: Boolean = false,
    minHeight: Dp = 64.dp,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    locked: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    // Compose only says "selected" when no other state description is set, so
    // a locked chip says both itself.
    val lockedDescription = stringResource(
        if (selected) R.string.state_selected_locked else R.string.state_not_selected_locked
    )
    val haptics = LocalHaptics.current
    val press = {
        if (locked) haptics?.rejected() else haptics?.tick()
        onClick()
    }

    ChipFrame(
        emphasized = selected,
        locked = locked,
        minHeight = minHeight,
        interaction = { source ->
            val choice = if (multiSelect) {
                Modifier.toggleable(
                    value = selected,
                    interactionSource = source,
                    indication = ripple(),
                    role = Role.Checkbox,
                    onValueChange = { press() },
                )
            } else {
                Modifier.selectable(
                    selected = selected,
                    interactionSource = source,
                    indication = ripple(),
                    role = Role.RadioButton,
                    onClick = press,
                )
            }
            choice.semantics { if (locked) stateDescription = lockedDescription }
        },
        modifier = modifier,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSizeFor(textStyle)),
            )
        }
        BoldWhenText(
            text = label,
            style = textStyle,
            bold = selected,
            modifier = Modifier.weight(1f),
        )
        // The checkmark's room is kept when it is not shown, so selecting a
        // chip never narrows its label, re-wrapping it.
        val checkSize = iconSizeFor(textStyle)
        Box(modifier = Modifier.size(checkSize)) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(checkSize),
                )
            }
        }
    }
}

/**
 * An on/off setting, drawn like a chip: "High contrast", then "ON" or "OFF"
 * and a switch at its end. On, it is filled with a thick border and bold text,
 * as a selected chip is, and the switch is solid with its knob to the end;
 * off, the switch is an outline with its knob at the start. The switch is what
 * tells it apart from a button: in High Contrast Light, a button and an "on"
 * setting were otherwise the same solid navy.
 *
 * A screen reader announces it as a switch named [title], such as "High
 * contrast, on, switch", rather than reading ON or OFF as part of its name.
 */
@Composable
fun SwitchChip(
    title: String,
    on: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val haptics = LocalHaptics.current
    ChipFrame(
        emphasized = on,
        locked = false,
        minHeight = 64.dp,
        interaction = { source ->
            Modifier.toggleable(
                value = on,
                interactionSource = source,
                indication = ripple(),
                role = Role.Switch,
                onValueChange = {
                    haptics?.tick()
                    onToggle()
                },
            )
        },
        modifier = modifier,
    ) {
        TitleThenEnd(
            modifier = Modifier.weight(1f),
            title = { BoldWhenText(text = title, style = textStyle, bold = on) },
            end = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The switch itself says on or off, so these are for the eye
                    // only. Both words are laid out and one is shown, so the
                    // title never moves.
                    Box(
                        contentAlignment = Alignment.CenterEnd,
                        modifier = Modifier.clearAndSetSemantics {},
                    ) {
                        Text(
                            stringResource(R.string.switch_state_on),
                            style = textStyle,
                            fontWeight = FontWeight.Bold,
                            modifier = if (on) Modifier else Modifier.alpha(0f),
                        )
                        Text(
                            stringResource(R.string.switch_state_off),
                            style = textStyle,
                            fontWeight = FontWeight.Bold,
                            modifier = if (on) Modifier.alpha(0f) else Modifier,
                        )
                    }
                    SwitchGraphic(on = on, height = iconSizeFor(textStyle))
                }
            },
        )
    }
}

/**
 * [title] with [end] beside it at the end of the line, or, when the title's
 * longest word wouldn't fit beside [end], with [end] on a line of its own
 * under it, still at the end. At large text sizes a title squeezed beside a
 * switch broke in the middle of its words ("navigat / ion").
 */
@Composable
private fun TitleThenEnd(
    title: @Composable () -> Unit,
    end: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(title, end), modifier = modifier) { (titles, ends), constraints ->
        val gap = TitleEndGap.roundToPx()
        val width = constraints.maxWidth
        val endPlaceable = ends.first().measure(Constraints(maxWidth = width))
        val longestWord = titles.first().minIntrinsicWidth(Constraints.Infinity)
        if (longestWord + gap + endPlaceable.width <= width) {
            val titlePlaceable = titles.first().measure(Constraints(maxWidth = width - gap - endPlaceable.width))
            val height = maxOf(titlePlaceable.height, endPlaceable.height)
            layout(width, height) {
                titlePlaceable.placeRelative(0, (height - titlePlaceable.height) / 2)
                endPlaceable.placeRelative(width - endPlaceable.width, (height - endPlaceable.height) / 2)
            }
        } else {
            val titlePlaceable = titles.first().measure(Constraints(maxWidth = width))
            layout(width, titlePlaceable.height + gap + endPlaceable.height) {
                titlePlaceable.placeRelative(0, 0)
                endPlaceable.placeRelative(width - endPlaceable.width, titlePlaceable.height + gap)
            }
        }
    }
}

private val TitleEndGap = 12.dp

/**
 * A switch's track and knob, [height] tall and growing with the text beside
 * it: solid with the knob at the end when [on], an outline with the knob at
 * the start when off. Drawn in the chip's text color, with an "on" knob in its
 * fill, so it follows the chip's colors in every theme and when pressed.
 */
@Composable
private fun SwitchGraphic(on: Boolean, height: Dp) {
    val ink = LocalContentColor.current
    val fill = LocalChipFill.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier = Modifier.size(width = height * 1.75f, height = height)) {
        val stroke = 2.dp.toPx()
        val radius = size.height / 2
        if (on) {
            drawRoundRect(color = ink, cornerRadius = CornerRadius(radius))
        } else {
            drawRoundRect(
                color = ink,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(radius - stroke / 2),
                style = Stroke(width = stroke),
            )
        }
        val atEnd = on != rtl
        drawCircle(
            color = if (on) fill else ink,
            radius = radius - 4.dp.toPx(),
            center = Offset(if (atEnd) size.width - radius else radius, size.height / 2),
        )
    }
}

/**
 * The look chips share. [emphasized] (selected, or on) fills the chip and
 * thickens its border. Pressed, its fill and text swap colors inside a thick
 * outline, and it shrinks a little; focused, it has a ring (see
 * ui/feedback/ControlFeedback). [interaction] is what a tap does and how it is
 * announced, given the chip's interaction source; it goes inside the chip's
 * shape, so the ripple keeps to it.
 */
@Composable
private fun ChipFrame(
    emphasized: Boolean,
    locked: Boolean,
    minHeight: Dp,
    interaction: (MutableInteractionSource) -> Modifier,
    modifier: Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val state = interactionSource.controlState()
    val colors = MaterialTheme.colorScheme
    val fill = when {
        locked -> Color.Transparent
        emphasized -> colors.primaryContainer
        else -> colors.surface
    }
    val text = when {
        locked -> colors.onSurfaceVariant
        emphasized -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    val borderColor = if (emphasized) colors.primary else colors.outline
    val containerColor = if (state.pressed) text else fill
    val contentColor = when {
        !state.pressed -> text
        locked -> colors.background
        else -> fill
    }
    val border = when {
        state.pressed -> Modifier.border(PressedBorderWidth, borderColor, VisorShapes.Control)
        locked -> Modifier.dashedBorder(2.dp, borderColor, VisorShapes.Control)
        else -> Modifier.border(if (emphasized) 2.dp else 1.dp, borderColor, VisorShapes.Control)
    }

    CompositionLocalProvider(
        LocalContentColor provides contentColor,
        LocalChipFill provides if (containerColor == Color.Transparent) colors.background else containerColor,
    ) {
        Row(
            modifier = modifier
                .minimumInteractiveComponentSize()
                .heightIn(min = minHeight)
                .controlFeedback(state, VisorShapes.Control, VisorTheme.focusRing)
                .clip(VisorShapes.Control)
                .background(containerColor)
                .then(border)
                .then(interaction(interactionSource))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * [text], bold when [bold], but laid out as if it always were: a chip turning
 * bold as it is selected then never re-wraps its label or changes height,
 * which would move everything below it.
 */
@Composable
private fun BoldWhenText(text: String, style: TextStyle, bold: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Text(
            text,
            style = style,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .alpha(0f)
                .clearAndSetSemantics {},
        )
        Text(text, style = style, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

/** The chip's fill, for a [SwitchGraphic] inside it to draw its "on" knob in. */
private val LocalChipFill = compositionLocalOf { Color.Unspecified }

/**
 * An icon size that grows with [style], as the text beside it does, rather
 * than staying 24dp beside text a user has made twice as large.
 */
@Composable
internal fun iconSizeFor(style: TextStyle): Dp =
    with(LocalDensity.current) { style.fontSize.toDp() * 1.3f }
