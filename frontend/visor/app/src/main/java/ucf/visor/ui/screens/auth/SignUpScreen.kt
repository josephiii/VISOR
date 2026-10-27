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

private const val TAG = "VISOR Sign up"

/**
 * NOTE: the email is only checked for its shape here (see [checkSignUp]);
 * ownership of the address is proven afterwards by the emailed code, entered on
 * the verify_account / enter_code screens, so [onSignUpClick] gets the email.
 *
 * @param talk says what went wrong when signing up fails, for a user who isn't
 *   looking at the screen (see [ErrorMessage]).
 */
@Composable
fun SignUpScreen(
    viewModel: VisorViewModel,
    onSignUpClick: (email: String) -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
    talk: (String, VoiceCategory) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val errorInView = remember { BringIntoViewRequester() }
    val usernameFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmedPassword by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    // Under each field once Sign up is pressed, until that field is changed.
    var fieldErrors by remember { mutableStateOf(SignUpFieldErrors(null, null, null, null)) }
    // What went wrong with the request itself, such as no connection.
    var requestError by remember { mutableStateOf<Int?>(null) }
    val scrollState = rememberScrollState()

    fun signUp() {
        if (isLoading) return
        requestError = null
        fieldErrors = checkSignUp(username, email, password, confirmedPassword)
        if (fieldErrors.any) {
            // Checked before the request. The first field with a problem takes
            // focus, so a screen reader reads it and its error, and VISOR says
            // the error for everyone else.
            val (firstError, focus) = listOf(
                fieldErrors.username to usernameFocus,
                fieldErrors.email to emailFocus,
                fieldErrors.password to passwordFocus,
                fieldErrors.confirmPassword to confirmFocus,
            ).first { (error, _) -> error != null }
            focus.requestFocus()
            if (firstError != null && !context.isScreenReaderOn()) {
                // MinPasswordLength fills in "Use at least %1$d characters."; the others have no blank.
                talk(
                    "${context.getString(R.string.error_prefix)} ${context.getString(firstError, MinPasswordLength)}",
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
            when (val result = AuthApi.register(username.trim(), email.trim(), password)) {
                is ApiResult.Success -> {
                    // Backend returns { "user_id": ... } — no token yet.
                    // The backend has emailed a code; the user verifies next.
                    isLoading = false
                    onSignUpClick(email.trim())
                }

                is ApiResult.Error -> {
                    isLoading = false
                    Log.w(TAG, "Signing up failed (${result.code}): ${result.message}")
                    requestError = authErrorMessage(result, AuthRequest.SIGN_UP)
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
            title = stringResource(R.string.sign_up_title),
            modifier = Modifier.padding(bottom = 16.dp),
        ) {
            VisorTextField(
                value = username,
                label = stringResource(R.string.username_label),
                onValueChange = {
                    username = it
                    fieldErrors = fieldErrors.copy(username = null)
                },
                imeAction = ImeAction.Next,
                errorMessage = fieldErrors.username?.let { stringResource(it) },
                modifier = Modifier.focusRequester(usernameFocus),
            )

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
                imeAction = ImeAction.Next,
                errorMessage = fieldErrors.password?.let { stringResource(it, MinPasswordLength) },
                modifier = Modifier.focusRequester(passwordFocus),
            )

            VisorTextField(
                value = confirmedPassword,
                label = stringResource(R.string.confirm_password_label),
                onValueChange = {
                    confirmedPassword = it
                    fieldErrors = fieldErrors.copy(confirmPassword = null)
                },
                isPassword = true,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { signUp() }),
                errorMessage = fieldErrors.confirmPassword?.let { stringResource(it) },
                modifier = Modifier.focusRequester(confirmFocus),
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                VisorButton(
                    text = stringResource(if (isLoading) R.string.sign_up_busy else R.string.sign_up_title),
                    width = 1f,
                    busy = isLoading,
                    onClick = { signUp() },
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
            stringResource(R.string.already_have_account),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        VisorButton(
            text = stringResource(R.string.login_title),
            onClick = onLoginClick,
        )
    }
}
