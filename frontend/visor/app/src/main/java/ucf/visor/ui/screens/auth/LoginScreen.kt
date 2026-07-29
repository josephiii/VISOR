package ucf.visor.ui.screens.auth

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ucf.visor.R
import ucf.visor.ui.components.VisorButton
import ucf.visor.ui.components.VisorHeader
import ucf.visor.ui.components.VisorTextField
import ucf.visor.wearables.WearablesViewModel

@Composable
fun LoginScreen(
    viewModel: WearablesViewModel?,
    onLoginClick: (email: String, password: String) -> Unit,
    onSignUpClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(24.dp)

    ) {

        VisorHeader()
        Spacer(modifier = modifier.height(20.dp))

        Card(
            modifier = Modifier
                .padding(
                    horizontal = 10.dp,
                    vertical = 35.dp)
                .border(
                    width = 2.dp,
                    color =MaterialTheme.colorScheme.onSurface,
                    shape = CutCornerShape(8.dp)
                ),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 20.dp)
            ) {

                Text(
                    text = stringResource(R.string.login_title),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                VisorTextField(
                    value = stringResource(R.string.example_email_input),
                    onValueChange = { typedAddress -> email = typedAddress }, // user input
                    label = stringResource(R.string.email_username_label)
                )

                VisorTextField(
                    value = stringResource(R.string.example_password_input),
                    onValueChange = { typedPassword -> password = typedPassword }, // user input
                    label = stringResource(R.string.password_label),
                    isPassword = true
                )

                VisorButton(
                    text = stringResource(R.string.login_title),
                    onClick = {
                        // toDashboard
                    }
                )
            }
        }

        Spacer(modifier = modifier.height(20.dp))
        Text("New users register here")
        VisorButton(
            text = stringResource(R.string.sign_up_title),
            width = 0.4f,
            onClick = {
                // toSignup
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewLoginScreen() {
    LoginScreen(
        viewModel = null,
        onLoginClick = { tempEmail, tempPassword -> {} },
        onSignUpClick = {}
    )
}