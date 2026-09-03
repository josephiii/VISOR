package ucf.visor.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// Basic Button implementation.
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
            .fillMaxWidth(fraction = width),
        shape = CutCornerShape(4.dp)
    ) {
        Text(text)
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