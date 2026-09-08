package ucf.visor.ui.screens.auth

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
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
import androidx.compose.ui.unit.sp
import ucf.visor.R
import ucf.visor.ui.components.AuthCodeInput
import ucf.visor.ui.components.VisorButton
import ucf.visor.ui.components.VisorHeader
import ucf.visor.ui.viewmodel.VisorViewModel

@Composable
fun EnterCodeScreen(
    viewModel: VisorViewModel,
    onCodeComplete: () -> Unit,
    onResendCodeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var code by remember { mutableStateOf("") }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(vertical = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {

        VisorHeader()

        Card(
            modifier = Modifier
                .padding(
                    horizontal = 35.dp,
                    vertical = 20.dp
                )
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = CutCornerShape(8.dp)
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
                    fontSize = 30.sp,
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
