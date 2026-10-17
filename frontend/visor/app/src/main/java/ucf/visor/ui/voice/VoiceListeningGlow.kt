package ucf.visor.ui.voice

import android.view.RoundedCorner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

/**
 * Shows that VISOR is listening for a voice command without covering any of
 * the screen: the screen's edges glow in the theme's primary color, breathing
 * slowly, for as long as VISOR listens.
 *
 * It replaces a "Listening for a command…" pop-up that sat over the top of the
 * screen, where most screens keep their heading. Screens keep their content in
 * from the edges, and a glow all the way around is easy to catch in peripheral
 * vision, often the part of their sight a low-vision user relies on most. It
 * is one of three cues: VISOR also says "Listening." out loud, and VisorLayout
 * buzzes the phone as listening starts and stops.
 *
 * The breathing is slow, about one breath every two seconds, nowhere near the
 * three flashes a second Section 508 warns about. With the system's animations
 * turned off, Compose holds the glow steady at full strength instead.
 */
@Composable
fun VoiceListeningGlow(state: VoiceNavState, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val cornerRadius = screenCornerRadius()

    AnimatedVisibility(
        visible = state != VoiceNavState.IDLE,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val strength by rememberInfiniteTransition(label = "listening").animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(BreathMillis), RepeatMode.Reverse),
            label = "glow strength",
        )
        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    // Breathes while the microphone is open, then holds while VISOR works.
                    drawEdgeGlow(color, if (state == VoiceNavState.LISTENING) strength else 1f, cornerRadius)
                }
        )
    }
}

/** The display's own corner radius in pixels, so the glow follows the screen's rounded corners. */
@Composable
private fun screenCornerRadius(): Float =
    LocalView.current.rootWindowInsets
        ?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
        ?.radius
        ?.toFloat()
        ?: 0f

private fun DrawScope.drawEdgeGlow(color: Color, strength: Float, cornerRadius: Float) {
    val edge = EdgeWidth.toPx()
    val glow = GlowWidth.toPx()

    // A crisp band right at the edge of the screen...
    drawRoundRect(
        color = color,
        topLeft = Offset(edge / 2, edge / 2),
        size = Size(size.width - edge, size.height - edge),
        cornerRadius = CornerRadius((cornerRadius - edge / 2).coerceAtLeast(0f)),
        style = Stroke(width = edge),
        alpha = strength,
    )

    // ...and a soft glow fading in from each side, over the screens' margins.
    val fade = listOf(color.copy(alpha = GlowAlpha * strength), Color.Transparent)
    drawRect(
        brush = Brush.verticalGradient(fade, startY = 0f, endY = glow),
        size = Size(size.width, glow),
    )
    drawRect(
        brush = Brush.verticalGradient(fade, startY = size.height, endY = size.height - glow),
        topLeft = Offset(0f, size.height - glow),
        size = Size(size.width, glow),
    )
    drawRect(
        brush = Brush.horizontalGradient(fade, startX = 0f, endX = glow),
        size = Size(glow, size.height),
    )
    drawRect(
        brush = Brush.horizontalGradient(fade, startX = size.width, endX = size.width - glow),
        topLeft = Offset(size.width - glow, 0f),
        size = Size(glow, size.height),
    )
}

private val EdgeWidth = 6.dp
private val GlowWidth = 28.dp
private const val GlowAlpha = 0.5f
private const val BreathMillis = 1_100
