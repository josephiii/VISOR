package ucf.visor.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ucf.visor.R

@Composable
fun SessionToggleButton(
    isSessionActive: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier
            .size(160.dp)
            .border(
                width = 5.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape
            )
    ) {
        Icon(
            imageVector = if (isSessionActive) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription =
                if (isSessionActive)
                    stringResource(R.string.end_session)
                else
                    stringResource(R.string.start_session),
            modifier = modifier.size(72.dp)
        )
    }
}