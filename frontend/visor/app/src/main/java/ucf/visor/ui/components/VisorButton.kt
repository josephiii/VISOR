package ucf.visor.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ucf.visor.R
import ucf.visor.ui.theme.VisorShapes

/**
 * Basic Button implementation. Enforces a touch target well above the
 * ~48dp minimum recommended for low-vision users (Material's own Button
 * default is only 40dp tall).
 *
 * @param locked shows the button as there but not usable yet: a lock and a
 *   dashed outline instead of the fill, with text still at full contrast (see
 *   [dashedBorder]). It still takes taps, so [onClick] can say what unlocks it.
 */
@Composable
fun VisorButton(
    text: String,
    width: Float = 0.75f,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
) {
    val lockedDescription = stringResource(R.string.state_locked)

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth(fraction = width)
            .heightIn(min = 56.dp)
            .then(
                if (locked) {
                    Modifier
                        .dashedBorder(2.dp, MaterialTheme.colorScheme.outline, VisorShapes.Control)
                        .semantics { stateDescription = lockedDescription }
                } else {
                    Modifier
                }
            ),
        shape = VisorShapes.Control,
        colors = if (locked) {
            ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        if (locked) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        AutoSizeText(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Preview
@Composable
fun PreviewVisorButton() {
    VisorButton(
        text = "Preview",
        onClick = {}
    )
}
