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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ucf.visor.R
import ucf.visor.ui.components.SessionToggleButton
import ucf.visor.ui.components.VisorHeader
import ucf.visor.ui.viewmodel.VisorViewModel

@Composable
fun HomeScreen(
    viewModel: VisorViewModel,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(all = 24.dp)
                .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        VisorHeader()
        Spacer(modifier = Modifier.height(48.dp))

        SessionToggleButton(
            isSessionActive = uiState.isSessionActive,
            onToggle = { viewModel.toggleSession() }
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            if (uiState.isSessionActive)
                stringResource(R.string.end_session)
            else
                stringResource(R.string.start_session)
        )
    }
}