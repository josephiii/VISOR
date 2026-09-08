package ucf.visor.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ucf.visor.R
import ucf.visor.ui.components.SessionToggleButton
import ucf.visor.ui.components.VisorHeader
import ucf.visor.ui.viewmodel.VisorViewModel
import ucf.visor.tts.Speaker

@Composable
fun HomeScreen(
    viewModel: VisorViewModel,
    talk: (String)->Unit={},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(all = 24.dp)
                .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        VisorHeader()
        Spacer(modifier = Modifier.height(100.dp))

        SessionToggleButton(
            isSessionActive = uiState.isSessionActive,
//PHASE 1: Button must take in context of current mode, and pass the text from mode into talk()
            onToggle = {
                viewModel.toggleSession()
                if(uiState.isSessionActive) talk("Session ended") else talk("Session started")
            }
        )

        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text =
                if (uiState.isSessionActive)
                    stringResource(R.string.end_session)
                else
                    stringResource(R.string.start_session),
            fontSize = 25.sp
        )
    }
}