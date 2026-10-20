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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ucf.visor.R
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

@Composable
fun EnterCodeScreen(
    viewModel: VisorViewModel,
    onCodeComplete: () -> Unit,
    onResendCodeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var code by remember { mutableStateOf("") }
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
                    onCodeComplete = {
                        onCodeComplete()
                    }
                )

                VisorButton(
                    text = stringResource(R.string.resend_code_button_text),
                    width = 1f,
                    onClick = {
                        onResendCodeClick()
                    }
                )
            }
        }
    }
}

