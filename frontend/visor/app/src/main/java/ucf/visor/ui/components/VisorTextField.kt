package ucf.visor.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview

// Text fields to collect user input (e.g., auth credentials).
// Can be used for usernames and passwords.
@Composable
fun VisorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier.fillMaxWidth(fraction = 0.9f)
    )
}

@Preview
@Composable
fun PreviewVisorTextField() {
    VisorTextField(
        value = "password string",
        onValueChange = { str: String -> str },
        label = "Test Password Text Field",
        isPassword = true // Set false to see text.
    )
}