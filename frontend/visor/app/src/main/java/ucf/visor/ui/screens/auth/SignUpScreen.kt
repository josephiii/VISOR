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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ucf.visor.R
import ucf.visor.net.AuthApi
import ucf.visor.ui.components.VisorButton
import ucf.visor.ui.components.VisorHeader
import ucf.visor.ui.components.VisorTextField
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.ui.theme.VisorShapes
import ucf.visor.ui.viewmodel.VisorViewModel

@Composable
fun SignUpScreen(
    viewModel: VisorViewModel,
    onSignUpClick: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmedPassword by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(vertical = 24.dp)
            .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline, xPosPadding = -6.0f)
            .verticalScroll(scrollState)
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
                    text = stringResource(R.string.sign_up_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )

                VisorTextField(
                    value = username,
                    label = stringResource(R.string.username_label),
                    onValueChange = { username = it },
                )

                VisorTextField(
                    value = email,
                    label = stringResource(R.string.email_label),
                    onValueChange = { email = it },
                )

                VisorTextField(
                    value = password,
                    label = stringResource(R.string.password_label),
                    onValueChange = { password = it },
                    isPassword = true
                )

                VisorTextField(
                    value = confirmedPassword,
                    label = stringResource(R.string.confirm_password_label),
                    onValueChange = { confirmedPassword = it },
                    isPassword = true
                )

                VisorButton(
                    text = stringResource(R.string.sign_up_title),
                    onClick = {
                        if (isLoading) return@VisorButton

                        errorMessage = validateSignUp(email, password, confirmedPassword)
                        if (errorMessage != null) return@VisorButton

                        isLoading = true
                        scope.launch {
                            when (val result = AuthApi.register(username, email, password)) {
                                is AuthApi.Result.Success -> {
                                    // Backend returns { "user_id": ... } — no token yet,
                                    // so the user still logs in next. Advance the flow.
                                    isLoading = false
                                    onSignUpClick()
                                }

                                is AuthApi.Result.Error -> {
                                    isLoading = false
                                    errorMessage = result.message
                                }
                            }
                        }
                    }
                )

                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        Text(stringResource(R.string.already_have_account))

        VisorButton(
            text = stringResource(R.string.login_title),
            width = 0.5f,
            onClick = {
                onLoginClick()
            }
        )
    }
}

/**
 * Cheap client-side checks before hitting the network. Returns an error message
 * to show the user, or null when the input looks valid. Uniqueness and the real
 * password policy are enforced by the backend.
 *
 * NOTE: We only sanity-check the email with a regex here — there is no email
 * verification yet (no confirmation code / verified flag). A registered account
 * is immediately usable. TODO: add email verification later (send a code on
 * register, gate login/onboarding on a verified status) and route sign-up back
 * through the verify_account / enter_code screens once that exists.
 */
private fun validateSignUp(
    email: String,
    password: String,
    confirmedPassword: String
): String? {
    val emailRegex = Regex("""^\w+@\w+\.\w+$""")
    return when {
        !emailRegex.matches(email) -> "Invalid email"
        password != confirmedPassword -> "The passwords do not match"
        else -> null
    }
}