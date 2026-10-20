package ucf.visor.ui.screens.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import ucf.visor.R
import ucf.visor.tts.SpokenPhrases
import ucf.visor.ui.components.SelectableChip
import ucf.visor.ui.components.SessionToggleButton
import ucf.visor.ui.components.VisorButton
import ucf.visor.ui.viewmodel.SessionMode
import ucf.visor.ui.viewmodel.VisorViewModel

@Composable
fun HomeScreen(
    viewModel: VisorViewModel,
    talk: (String) -> Unit = {},
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
    // Takes a picture with the glasses and answers it for the session's mode (MainActivity).
    onCapture: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()

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
                onToggle = { viewModel.toggleSession(onRequestWearablesPermission, talk) }
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

            // During a session: the capture button, then the mode that decides what it does.
            if (uiState.isSessionActive) {
                VisorButton(
                    text = stringResource(captureLabel(session.mode)),
                    width = 1f,
                    onClick = onCapture,
                )

                ModePicker(
                    selected = session.mode,
                    onSelect = { mode ->
                        viewModel.setMode(mode)
                        talk(SpokenPhrases.modeActivated(mode))
                    },
                )
            }
        }
    }
}

@Composable
private fun ModePicker(
    selected: SessionMode,
    onSelect: (SessionMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.home_mode_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        SessionMode.entries.forEach { mode ->
            SelectableChip(
                label = stringResource(modeLabel(mode)),
                selected = mode == selected,
                modifier = Modifier.fillMaxWidth(),
                onClick = { if (mode != selected) onSelect(mode) },
            )
        }
    }
}

@StringRes
private fun modeLabel(mode: SessionMode): Int = when (mode) {
    SessionMode.HAZARD -> R.string.hazard_mode
    SessionMode.SCENE -> R.string.scene_mode
    SessionMode.READER -> R.string.reader_mode
}

@StringRes
private fun captureLabel(mode: SessionMode): Int = when (mode) {
    SessionMode.HAZARD -> R.string.home_capture_hazard
    SessionMode.SCENE -> R.string.home_capture_scene
    SessionMode.READER -> R.string.home_capture_reader
}
