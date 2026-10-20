package ucf.visor.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ucf.visor.R
import ucf.visor.ui.components.VisorButton
import ucf.visor.ui.components.VisorTextField
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.ui.viewmodel.VisorViewModel

@Composable
fun ForgotPasswordScreen(
    viewModel: VisorViewModel,
    onSendCodeClick: () -> Unit,
    onResetPasswordClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline)
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState)
            .padding(vertical = 24.dp),
    ) {
        AuthCard(title = stringResource(R.string.forgot_password_title)) {
            Text(
                text = stringResource(R.string.forgot_password_description),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            // The user's own email, typed in: it used to show a fixed
            // placeholder address that could not be changed.
            VisorTextField(
                value = email,
                onValueChange = { email = it },
                label = stringResource(R.string.email_label),
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
            )

            VisorButton(
                text = stringResource(R.string.send_code_button_text),
                width = 1f,
                onClick = onSendCodeClick,
            )

            Text(
                text = stringResource(R.string.reset_or),
                style = MaterialTheme.typography.bodyLarge,
            )

            VisorButton(
                text = stringResource(R.string.reset_password_title),
                width = 1f,
                onClick = onResetPasswordClick,
            )
        }
    }
}
