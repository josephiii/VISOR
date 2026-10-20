package ucf.visor.ui.screens.auth

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ucf.visor.R
import ucf.visor.data.VoiceCategory
import ucf.visor.network.ApiResult
import ucf.visor.network.AuthApi
import ucf.visor.ui.components.ErrorMessage
import ucf.visor.ui.components.VisorButton
import ucf.visor.ui.components.VisorTextField
import ucf.visor.ui.components.isScreenReaderOn
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.ui.viewmodel.VisorViewModel

private const val TAG = "VISOR Login"

/**
 * @param talk says what went wrong when logging in fails, for a user who isn't
 *   looking at the screen (see [ErrorMessage]).
 */
@Composable
fun LoginScreen(
    viewModel: VisorViewModel,
    onLoginSuccess: (accessToken: String, refreshToken: String) -> Unit,
    onForgotPasswordClick: () -> Unit,
    onSignUpClick: () -> Unit,
    onUnverified: (email: String) -> Unit,
    modifier: Modifier = Modifier,
    talk: (String, VoiceCategory) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val errorInView = remember { BringIntoViewRequester() }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    // Under each field once Log in is pressed, until that field is changed.
    var fieldErrors by remember { mutableStateOf(LoginFieldErrors(email = null, password = null)) }
    // What went wrong with the request itself, such as no connection.
    var requestError by remember { mutableStateOf<Int?>(null) }
    val scrollState = rememberScrollState()

    fun logIn() {
        if (isLoading) return
        requestError = null
        fieldErrors = checkLogin(email, password)
        if (fieldErrors.any) {
            // Checked before the request, not after a wait for the server. The
            // first field with a problem takes focus, so a screen reader reads
            // it and its error, and VISOR says the error for everyone else.
            val firstError = fieldErrors.email ?: fieldErrors.password
            if (fieldErrors.email != null) emailFocus.requestFocus() else passwordFocus.requestFocus()
            if (firstError != null && !context.isScreenReaderOn()) {
                talk(
                    "${context.getString(R.string.error_prefix)} ${context.getString(firstError)}",
                    VoiceCategory.ANSWERS,
                )
            }
            return
        }

        // The keyboard would cover the button's progress, and any error under it.
        keyboard?.hide()
        focusManager.clearFocus()
        isLoading = true
        scope.launch {
            when (val result = AuthApi.login(email.trim(), password)) {
                is ApiResult.Success -> {
                    // Backend returns { "access_token", "refresh_token" }.
                    // Hand them to the ViewModel, which persists the
                    // session and navigates home.
                    val accessToken = result.json.optString("access_token")
                    val refreshToken = result.json.optString("refresh_token")
                    isLoading = false
                    onLoginSuccess(accessToken, refreshToken)
                }

                is ApiResult.Error -> {
                    isLoading = false
                    // 403 = registered but never entered the emailed code:
                    // send a fresh one and go finish verifying.
                    if (result.code == 403) {
                        AuthApi.resendCode(email.trim())
                        onUnverified(email.trim())
                        return@launch
                    }
                    Log.w(TAG, "Logging in failed (${result.code}): ${result.message}")
                    requestError = authErrorMessage(result, AuthRequest.LOG_IN)
                }
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline)
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState)
            .padding(vertical = 24.dp),
    ) {
        AuthCard(
            title = stringResource(R.string.login_title),
            modifier = Modifier.padding(bottom = 16.dp),
        ) {
            VisorTextField(
                value = email,
                label = stringResource(R.string.email_label),
                onValueChange = {
                    email = it
                    fieldErrors = fieldErrors.copy(email = null)
                },
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                errorMessage = fieldErrors.email?.let { stringResource(it) },
                modifier = Modifier.focusRequester(emailFocus),
            )

            VisorTextField(
                value = password,
                label = stringResource(R.string.password_label),
                onValueChange = {
                    password = it
                    fieldErrors = fieldErrors.copy(password = null)
                },
                isPassword = true,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { logIn() }),
                errorMessage = fieldErrors.password?.let { stringResource(it) },
                modifier = Modifier.focusRequester(passwordFocus),
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                VisorButton(
                    text = stringResource(if (isLoading) R.string.login_busy else R.string.login_title),
                    width = 1f,
                    busy = isLoading,
                    onClick = { logIn() },
                )
                ErrorMessage(
                    message = requestError?.let { stringResource(it) },
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .bringIntoViewRequester(errorInView),
                    talk = talk,
                )
                // At large text sizes the error starts below the screen's edge.
                LaunchedEffect(requestError) {
                    if (requestError != null) errorInView.bringIntoView()
                }
            }
        }

        Text(
            stringResource(R.string.login_trouble),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        VisorButton(
            text = stringResource(R.string.forgot_password_title),
            onClick = onForgotPasswordClick,
        )

        Text(
            stringResource(R.string.login_new_users),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        VisorButton(
            text = stringResource(R.string.sign_up_title),
            onClick = onSignUpClick,
        )
    }
}
