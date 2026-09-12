package ucf.visor.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ucf.visor.ui.theme.VisorShapes

// Basic Button implementation. Enforces a touch target well above the
// ~48dp minimum recommended for low-vision users (Material's own Button
// default is only 40dp tall).
@Composable
fun VisorButton(
    text: String,
    width: Float = 0.75f,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth(fraction = width)
            .heightIn(min = 56.dp),
        shape = VisorShapes.Control
    ) {
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