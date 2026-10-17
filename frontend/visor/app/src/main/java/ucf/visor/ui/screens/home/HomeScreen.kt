package ucf.visor.ui.screens.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ucf.visor.R
import ucf.visor.data.VoiceCategory
import ucf.visor.tts.SpokenPhrases
import ucf.visor.ui.components.SelectableChip
import ucf.visor.ui.components.SessionToggleButton
import ucf.visor.ui.components.VisorButton
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.ui.viewmodel.SessionMode
import ucf.visor.ui.viewmodel.VisorViewModel

@Composable
fun HomeScreen(
    viewModel: VisorViewModel,
    talk: (String, VoiceCategory) -> Unit = { _, _ -> },
    // Starts or ends the session, and says so (MainActivity).
    onToggleSession: () -> Unit = {},
    // Takes a picture with the glasses and answers it for the session's mode (MainActivity).
    onCapture: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    // The capture button and the modes only work during a session, but they are
    // on screen before one too, locked. That shows early what a session offers,
    // and gives the screen the same layout either way, so the session button
    // never jumps when a session starts. Locked controls stay full contrast
    // (dashed, not faded) and still answer a tap, saying what to do first.
    val locked = !uiState.isSessionActive

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportHeight = maxHeight

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline)
                .verticalScroll(scrollState)
                .heightIn(min = viewportHeight)
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            SessionToggleButton(
                isSessionActive = uiState.isSessionActive,
                onToggle = onToggleSession,
            )

            // Both labels are laid out and only one is shown: at large text sizes
            // "Start Session" can wrap where "End Session" does not, and a label
            // that changed height would move the button above it.
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.start_session),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.shownOnlyIf(!uiState.isSessionActive),
                )
                Text(
                    text = stringResource(R.string.end_session),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.shownOnlyIf(uiState.isSessionActive),
                )
            }

            // The capture button, then the mode that decides what it does.
            VisorButton(
                text = stringResource(captureLabel(session.mode)),
                width = 1f,
                locked = locked,
                onClick = {
                    if (locked) {
                        talk(SpokenPhrases.startSessionToCapture(session.mode), VoiceCategory.SESSION_UPDATES)
                    } else {
                        onCapture()
                    }
                },
            )

            ModePicker(
                selected = session.mode,
                locked = locked,
                onSelect = { mode ->
                    viewModel.setMode(mode)
                    talk(SpokenPhrases.modeActivated(mode), VoiceCategory.SESSION_UPDATES)
                },
                onLockedClick = {
                    talk(SpokenPhrases.START_SESSION_TO_SWITCH_MODES, VoiceCategory.SESSION_UPDATES)
                },
            )

            // What the locked controls are waiting for. Its space is kept during
            // a session too, for the same reason as the label's above.
            Row(
                modifier = Modifier.shownOnlyIf(locked),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                Text(
                    text = stringResource(R.string.home_locked_hint),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

/** Keeps an element's space while hiding it from sight and from screen readers. */
private fun Modifier.shownOnlyIf(shown: Boolean): Modifier =
    if (shown) this else alpha(0f).clearAndSetSemantics {}

@Composable
private fun ModePicker(
    selected: SessionMode,
    locked: Boolean,
    onSelect: (SessionMode) -> Unit,
    onLockedClick: () -> Unit,
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
                locked = locked,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (locked) onLockedClick()
                    else if (mode != selected) onSelect(mode)
                },
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
