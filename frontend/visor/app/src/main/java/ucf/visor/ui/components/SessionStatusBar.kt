package ucf.visor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ucf.visor.R
import ucf.visor.ui.feedback.LocalHaptics
import ucf.visor.ui.feedback.controlFeedback
import ucf.visor.ui.feedback.controlState
import ucf.visor.ui.theme.VisorShapes
import ucf.visor.ui.theme.VisorTheme
import ucf.visor.ui.viewmodel.SessionMode
import ucf.visor.ui.viewmodel.icon
import ucf.visor.ui.viewmodel.labelRes

/** Between the dot, the mode's icon and the mode's name. */
private val PartGap = 10.dp

/**
 * Whether a session is running, at the top of every screen once logged in:
 * "● ⚠ Hazard Awareness", or "○ Session off". A session goes on across every
 * screen, but Home's button was the only place it showed. The top edge is
 * where a user with central vision loss can catch it.
 *
 * On, it is filled, with a solid dot, and shows the session's mode; off, it is
 * an outline with a hollow dot and says "Session off". So the state shows by
 * shape and words as well as color, without "Session on" in front: Home's
 * button already says how to start and end one. It is always there, on or off,
 * and always one line tall, so starting a session never moves the screen below
 * it. Tapping it goes Home, where a session is started and ended.
 *
 * Text is never shrunk or cut off to keep it to one line (WCAG 1.4.4). While a
 * session runs it has two styles, at full size:
 *
 * 1. ● ⚠ Hazard Awareness
 * 2. ● ⚠
 *
 * The first is shown only when every mode's name fits, so a text size has one
 * style for all three modes, and switching modes never changes how the bar
 * looks. The mode's icon is always there, so what remains still says which
 * mode it is. The icon is the one beside each mode on Home, so it can be
 * learned there, at full size, next to its name. Off, it says "Session off",
 * or "Off" where that doesn't fit. A screen reader always hears everything:
 * "Session on, Hazard Awareness".
 */
@Composable
fun SessionStatusBar(
    active: Boolean,
    mode: SessionMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    val state = interactionSource.controlState()
    val modeName = stringResource(mode.labelRes)
    val modeNames = SessionMode.entries.map { stringResource(it.labelRes) }
    val spoken = if (active) stringResource(R.string.session_status_on_spoken, modeName)
    else stringResource(R.string.session_status_off)
    val goHome = stringResource(R.string.session_status_go_home)
    val contentColor = if (active) colors.onPrimary else colors.onSurface
    val style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    val offWords = stringResource(R.string.session_status_off)
    val offShortWords = stringResource(R.string.session_status_off_short)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .controlFeedback(state, VisorShapes.Control, VisorTheme.focusRing)
                .clip(VisorShapes.Control)
                .background(if (active) colors.primary else Color.Transparent)
                .border(2.dp, if (active) colors.primary else colors.outline, VisorShapes.Control)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClickLabel = goHome,
                    role = Role.Button,
                    onClick = {
                        haptics?.tick()
                        onClick()
                    },
                )
                // Named here, for a screen reader to hear in full.
                .semantics { contentDescription = spoken }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val fits = rememberOneLineFit(style, maxWidth)
            val dotSize = iconSizeFor(style) * 0.7f
            val iconSize = iconSizeFor(style)

            Row(
                horizontalArrangement = Arrangement.spacedBy(PartGap),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics {},
            ) {
                StatusDot(on = active, color = if (active) colors.onPrimary else colors.outline, size = dotSize)
                if (active) {
                    Icon(
                        imageVector = mode.icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(iconSize),
                    )
                    // Named only if the longest mode's name would fit too.
                    if (modeNames.all { fits(it, dotSize + iconSize + PartGap * 2) }) {
                        StatusText(modeName, style, contentColor, wrap = false)
                    }
                } else {
                    // "Off" may still not fit at a size no phone has yet; it
                    // wraps then, rather than be cut off.
                    val long = fits(offWords, dotSize + PartGap)
                    StatusText(if (long) offWords else offShortWords, style, contentColor, wrap = !long)
                }
            }
        }
    }
}

/**
 * Whether some text, in [style] on one line, and [extra] room besides, fits
 * across [maxWidth].
 */
@Composable
private fun rememberOneLineFit(style: TextStyle, maxWidth: Dp): (String, Dp) -> Boolean {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(measurer, density, style, maxWidth) {
        { text, extra ->
            val textWidth = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
            with(density) { textWidth.toDp() + extra } <= maxWidth
        }
    }
}

@Composable
private fun StatusText(text: String, style: TextStyle, color: Color, wrap: Boolean) {
    Text(text, style = style, color = color, softWrap = wrap, maxLines = if (wrap) Int.MAX_VALUE else 1)
}

/** Solid while a session runs, hollow while none does. */
@Composable
private fun StatusDot(on: Boolean, color: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val radius = this.size.minDimension / 2
        if (on) {
            drawCircle(color = color, radius = radius)
        } else {
            val stroke = 2.dp.toPx()
            drawCircle(color = color, radius = radius - stroke / 2, style = Stroke(width = stroke))
        }
    }
}
