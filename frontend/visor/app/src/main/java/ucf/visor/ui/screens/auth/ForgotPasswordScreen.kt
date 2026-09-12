package ucf.visor.ui.screens.auth

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ucf.visor.R
import ucf.visor.ui.components.VisorButton
import ucf.visor.ui.components.VisorHeader
import ucf.visor.ui.components.VisorTextField
import ucf.visor.ui.theme.VisorShapes
import ucf.visor.ui.viewmodel.VisorViewModel

@Composable
fun ForgotPasswordScreen(
    viewModel: VisorViewModel,
    onSendCodeClick: () -> Unit,
    onResetPasswordClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(vertical = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {

        VisorHeader()

        Card(
            shape = VisorShapes.Control,
            modifier = Modifier
                .padding(
                    horizontal = 35.dp,
                    vertical = 20.dp
                )
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = VisorShapes.Control
                ),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 20.dp)
            ) {

                Text(
                    text = stringResource(R.string.forgot_password_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = stringResource(R.string.forgot_password_description),
                    style = MaterialTheme.typography.bodyMedium,
                )

                VisorTextField(
                    value = stringResource(R.string.example_email_input),
                    onValueChange = { email = it }, // user input
                    label = stringResource(R.string.email_label)
                )

                VisorButton(
                    text = stringResource(R.string.send_code_button_text),
                    width = 0.5f,
                    onClick = {
                        onSendCodeClick()
                    }
                )

                Text(
                    text = stringResource(R.string.reset_or),
                    style = MaterialTheme.typography.bodyMedium,
                )

                VisorButton(
                    text = stringResource(R.string.reset_password_title),
                    onClick = {
                        onResetPasswordClick()
                    }
                )

            }
        }
    }
}