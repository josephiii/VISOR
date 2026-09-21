package ucf.visor.ui.screens.auth

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import ucf.visor.ui.components.AuthCodeInput
import ucf.visor.ui.components.VisorButton
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.ui.theme.VisorShapes
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
                .verticalScroll(scrollState)
                .heightIn(min = viewportHeight * BottomThirdKeptClear)
                .padding(vertical = 24.dp)
        ) {
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
                    modifier = Modifier
                        .padding(vertical = 20.dp)
                ) {

                    Text(
                        text = stringResource(R.string.enter_code_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    AuthCodeInput(
                        onCodeComplete = {
                            onCodeComplete()
                        }
                    )

                    VisorButton(
                        text = stringResource(R.string.resend_code_button_text),
                        onClick = {
                            onResendCodeClick()
                        }
                    )


                }
            }
        }
    }
}

