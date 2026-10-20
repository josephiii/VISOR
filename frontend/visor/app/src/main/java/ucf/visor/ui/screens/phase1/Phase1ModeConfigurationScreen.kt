package ucf.visor.ui.screens.phase1

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ucf.visor.R
import ucf.visor.data.VoiceCategory
import ucf.visor.tts.SpokenPhrases
import ucf.visor.ui.components.ScreenTitle
import ucf.visor.ui.components.SelectableChip
import ucf.visor.ui.components.SwitchButton
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.ui.viewmodel.SessionMode
import ucf.visor.ui.viewmodel.VisorViewModel
import ucf.visor.ui.viewmodel.labelRes

/**
 * The Phase 1 demo: pick a mode, then a script stands in for VISOR's answer
 * to a photo. Its title and buttons follow VISOR's text size like every other
 * screen, and each script is shown whole, wrapping, rather than cut off after
 * 25 characters.
 */
@Composable
fun Phase1ModeConfigurationScreen(
    viewModel: VisorViewModel,
    talk: (String, VoiceCategory) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    val scriptsArrayRes = when (session.mode) {
        SessionMode.HAZARD -> R.array.hazard_awareness_scripts
        SessionMode.SCENE -> R.array.scene_description_scripts
        SessionMode.READER -> R.array.reading_assistance_scripts
    }

    val scripts = stringArrayResource(scriptsArrayRes)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline)
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState)
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle(
            stringResource(R.string.p1_mode_config_screen_title),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SessionMode.entries.forEach { mode ->
                val isSelected = mode == session.mode
                SelectableChip(
                    label = stringResource(mode.labelRes),
                    selected = isSelected,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        // Switch modes when selecting a non-active mode
                        if (!isSelected && uiState.isSessionActive) {
                            viewModel.setMode(mode)
                            talk(SpokenPhrases.modeActivated(mode), VoiceCategory.SESSION_UPDATES)
                        }
                    },
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            scripts.forEachIndexed { index, scriptText ->
                SwitchButton(
                    label = stringResource(R.string.p1_script, index + 1, scriptText),
                    onClick = {
                        // A demo script stands in for an answer, shown on Home as one is.
                        if (uiState.isSessionActive) {
                            viewModel.showAnswer(scriptText)
                            talk(scriptText, VoiceCategory.ANSWERS)
                        }
                    },
                )
            }
        }
    }
}
