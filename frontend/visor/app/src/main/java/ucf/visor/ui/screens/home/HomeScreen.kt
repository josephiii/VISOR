package ucf.visor.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import ucf.visor.ui.viewmodel.VisorViewModel

@Composable
fun HomeScreen(
    viewModel: VisorViewModel,
    talk: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Start the session control in the middle of the screen. It is the only thing
    // a user comes to this screen to do, and the one control they may be hunting
    // for with very little usable field of view, so it belongs where the eye and
    // the thumb both land first rather than pinned under a fixed top spacer.
    //
    // Centring and scrolling fight each other: inside a verticalScroll the height
    // is unbounded, so a Center arrangement has nothing to centre against and the
    // content just stacks from the top. Giving the column a minimum height of one
    // viewport resolves it - the arrangement centres within that screenful, and
    // the column still grows and scrolls when a large text size overflows it.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportHeight = maxHeight

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .padding(all = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            SessionToggleButton(
                isSessionActive = uiState.isSessionActive,
                onToggle = {
                    viewModel.toggleSession()
                    if (uiState.isSessionActive) {
                        talk("Ending Session")
                    } else {
                        talk("Starting Session")
                    }
                }
            )

            Text(
                text =
                    if (uiState.isSessionActive) {
                        stringResource(R.string.end_session)
                    } else {
                        stringResource(R.string.start_session)
                    },
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}
