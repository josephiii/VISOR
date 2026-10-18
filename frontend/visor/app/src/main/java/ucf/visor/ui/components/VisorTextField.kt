package ucf.visor.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ucf.visor.R
import ucf.visor.ui.theme.VisorShapes

/**
 * Text fields to collect user input (e.g., auth credentials).
 * Can be used for usernames and passwords.
 *
 * @param errorMessage what is wrong with what was typed, such as "Enter your
 *   email.", or null. It shows under the field as "Error: …" with an error
 *   icon, not only as a red outline, and a screen reader says it as it appears
 *   and as part of the field (WCAG 3.3.1).
 */
@Composable
fun VisorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val prefix = stringResource(R.string.error_prefix)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation =
            if (isPassword)
                PasswordVisualTransformation()
            else
                VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = keyboardActions,
        singleLine = true,
        isError = errorMessage != null,
        trailingIcon = errorMessage?.let {
            {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = null,
                    modifier = Modifier.size(iconSizeFor(MaterialTheme.typography.bodyLarge)),
                )
            }
        },
        supportingText = errorMessage?.let { message ->
            {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(prefix) }
                        append(" ")
                        append(message)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        },
        shape = VisorShapes.Control,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            // Said in place of Material's generic "Invalid input".
            .then(if (errorMessage != null) Modifier.semantics { error(errorMessage) } else Modifier)
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
