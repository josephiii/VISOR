package ucf.visor.ui.screens.auth

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.ui.theme.VisorShapes
import ucf.visor.ui.viewmodel.VisorViewModel

@Composable
fun LoginScreen(
    viewModel: VisorViewModel,
    onLoginClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onSignUpClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(24.dp)
            .verticalScroll(scrollState)
            .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline)
    ) {

        VisorHeader()
        Spacer(modifier = modifier.height(20.dp))

        Card(
            shape = VisorShapes.Control,
            modifier = Modifier
                .padding(
                    horizontal = 10.dp,
                    vertical = 35.dp
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
                    text = stringResource(R.string.login_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                VisorTextField(
                    value = email,
                    label = stringResource(R.string.email_username_label),
                    onValueChange = { email = it }, // user input
                )

                VisorTextField(
                    value = password,
                    label = stringResource(R.string.password_label),
                    onValueChange = { password = it }, // user input
                    isPassword = true
                )

                VisorButton(
                    text = stringResource(R.string.login_title),
                    onClick = {
                        onLoginClick()
                    }
                )
            }
        }

        Spacer(modifier = modifier.height(10.dp))
        Text("Trouble logging in?")
        VisorButton(
            text = stringResource(R.string.forgot_password_title),
            width = 0.65f,
            onClick = {
                onForgotPasswordClick()
            }
        )

        Spacer(modifier = modifier.height(20.dp))
        Text("New users register here")
        VisorButton(
            text = stringResource(R.string.sign_up_title),
            width = 0.4f,
            onClick = {
                onSignUpClick()
            }
        )
    }
}