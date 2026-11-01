package ucf.visor.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ucf.visor.R
import ucf.visor.network.ApiResult
import ucf.visor.network.AuthApi
import ucf.visor.ui.components.AuthCodeInput
import ucf.visor.ui.components.VisorButton
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.ui.viewmodel.VisorViewModel

/**
 * How much of the screen the code card is allowed to occupy, measured from the
 * top. The remaining bottom third is left clear so the card does not sit where
 * the keyboard appears once the code field takes focus.
 */
private const val BottomThirdKeptClear = 2f / 3f

/**
 * Shared by two flows. When [verifyEmail] is set, the code is checked against
 * the backend and [onVerified] receives the new session tokens; Resend sends a
 * fresh email. Otherwise (forgot password, still a stub) [onCodeComplete] runs
 * as soon as the last digit is typed.
 */
@Composable
fun EnterCodeScreen(
    viewModel: VisorViewModel,
    onCodeComplete: () -> Unit,
    onResendCodeClick: () -> Unit,
    modifier: Modifier = Modifier,
    verifyEmail: String? = null,
    onVerified: (accessToken: String, refreshToken: String) -> Unit = { _, _ -> }
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportHeight = maxHeight

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxWidth()
                .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState)
                .heightIn(min = viewportHeight * BottomThirdKeptClear)
                .padding(vertical = 24.dp)
        ) {
            AuthCard(title = stringResource(R.string.enter_code_title)) {
                AuthCodeInput(
                    onCodeComplete = { code ->
                        if (verifyEmail == null) {
                            onCodeComplete()
                        } else if (!isLoading) {
                            isLoading = true
                            message = null
                            scope.launch {
                                when (val result = AuthApi.verifyEmail(verifyEmail, code)) {
                                    is ApiResult.Success -> {
                                        isLoading = false
                                        onVerified(
                                            result.json.optString("access_token"),
                                            result.json.optString("refresh_token")
                                        )
                                    }

                                    is ApiResult.Error -> {
                                        isLoading = false
                                        isError = true
                                        message = result.message
                                    }
                                }
                            }
                        }
                    }
                )

                VisorButton(
                    text = stringResource(R.string.resend_code_button_text),
                    width = 1f,
                    onClick = {
                        if (verifyEmail == null) {
                            onResendCodeClick()
                        } else {
                            scope.launch {
                                when (val result = AuthApi.resendCode(verifyEmail)) {
                                    is ApiResult.Success -> {
                                        isError = false
                                        message = "A new code was sent"
                                    }

                                    is ApiResult.Error -> {
                                        isError = true
                                        message = result.message
                                    }
                                }
                            }
                        }
                    }
                )

                message?.let {
                    Text(
                        text = it,
                        color = if (isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

