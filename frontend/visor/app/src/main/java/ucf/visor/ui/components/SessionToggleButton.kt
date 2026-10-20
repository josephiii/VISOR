package ucf.visor.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ucf.visor.R
import ucf.visor.ui.feedback.animationsEnabled
import ucf.visor.ui.feedback.controlFeedback
import ucf.visor.ui.feedback.controlState
import ucf.visor.ui.theme.VisorTheme

/**
 * Home's session button. While a session runs, it shows Stop in the error
 * colors, and a ring breathes slowly around it, one breath about every five
 * seconds. In VISOR, a breathing ring around this button means a session is
 * running, and means nothing else. With the system's animations off, the ring
 * holds still at full strength.
 */
@Composable
fun SessionToggleButton(
    isSessionActive: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isSessionActive) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (isSessionActive) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val ringColor = MaterialTheme.colorScheme.errorContainer
    val interactionSource = remember { MutableInteractionSource() }
    val state = interactionSource.controlState()
    val breath: State<Float>? = if (isSessionActive && animationsEnabled()) {
        rememberInfiniteTransition(label = "session").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(BreathMillis), RepeatMode.Reverse),
            label = "session ring",
        )
    } else {
        null
    }

    Button(
        onClick = onToggle,
        interactionSource = interactionSource,
        modifier = modifier
            .size(160.dp)
            .controlFeedback(state, CircleShape, VisorTheme.focusRing)
            .drawBehind {
                if (isSessionActive) {
                    val stroke = RingWidth.toPx()
                    drawCircle(
                        color = ringColor,
                        radius = size.minDimension / 2 + RingGap.toPx() + stroke / 2,
                        style = Stroke(width = stroke),
                        // Read here, as it is drawn, so the breathing redraws
                        // the ring without recomposing the button.
                        alpha = breath?.value ?: 1f,
                    )
                }
            },
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        border = BorderStroke(4.dp, MaterialTheme.colorScheme.outline),
    ) {
        Icon(
            imageVector = if (isSessionActive) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription =
                if (isSessionActive)
                    stringResource(R.string.end_session)
                else
                    stringResource(R.string.start_session),
            modifier = Modifier.size(72.dp)
        )
    }
}

private val RingGap = 6.dp
private val RingWidth = 8.dp

// One way of the breath; there and back is about five seconds.
private const val BreathMillis = 2_400
