package ucf.visor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ucf.visor.R
import ucf.visor.ui.feedback.LocalHaptics
import ucf.visor.ui.feedback.controlFeedback
import ucf.visor.ui.feedback.controlState
import ucf.visor.ui.theme.VisorShapes
import ucf.visor.ui.theme.VisorTheme
import ucf.visor.ui.viewmodel.SessionMode
import ucf.visor.ui.viewmodel.labelRes

/**
 * Whether a session is running, at the top of every screen once logged in:
 * "Session on · Hazard Awareness", or "Session off". A session goes on across
 * every screen, but Home's button was the only place it showed. The top edge
 * is where a user with central vision loss can catch it.
 *
 * On, it is filled, with a solid dot; off, it is an outline with a hollow
 * one, so the state shows by shape and words as well as color. It is always
 * there, on or off, so starting a session never moves the screen below it.
 * Tapping it goes Home, where a session is started and ended.
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
    val shown = if (active) stringResource(R.string.session_status_on, modeName) else stringResource(R.string.session_status_off)
    val spoken = if (active) stringResource(R.string.session_status_on_spoken, modeName) else shown
    val goHome = stringResource(R.string.session_status_go_home)
    val textColor = if (active) colors.onPrimary else colors.onSurface
    val style = MaterialTheme.typography.titleMedium

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
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
                .semantics { contentDescription = spoken }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dot = if (active) colors.onPrimary else colors.outline
            Canvas(modifier = Modifier.size(iconSizeFor(style) * 0.7f)) {
                val radius = size.minDimension / 2
                if (active) {
                    drawCircle(color = dot, radius = radius)
                } else {
                    val stroke = 2.dp.toPx()
                    drawCircle(color = dot, radius = radius - stroke / 2, style = Stroke(width = stroke))
                }
            }
            // Named for a screen reader above, without the "·" it would read aloud.
            Text(
                shown,
                style = style,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}
