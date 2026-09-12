package ucf.visor.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ucf.visor.R

// The main session control — VISOR's single most important button. Previously
// a thin ring outline around a small, low-contrast icon (easy to miss at a
// glance); now a solid color-filled disc so "session is running" is readable
// as a color change, not just an icon swap, and Stop reads as a distinct
// (error-toned) action from Start rather than looking identical to it.
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

    Button(
        onClick = onToggle,
        modifier = modifier.size(160.dp),
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