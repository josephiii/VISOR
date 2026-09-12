package ucf.visor.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ucf.visor.ui.theme.VisorShapes

@Composable
fun SwitchButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
) {
    Button(
        modifier = modifier
            .height(56.dp)
            .fillMaxWidth(),
        onClick = onClick,
        enabled = enabled,
        shape = VisorShapes.Control,
        colors = if (isDestructive) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Preview
@Composable
fun SwitchButtonPreview() {
    SwitchButton(
        onClick = {},
        label = "Test Label"
    )
}
