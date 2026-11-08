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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ucf.visor.R
import ucf.visor.data.VoiceCategory
import ucf.visor.stt.CameraCommand
import ucf.visor.ui.components.ScreenTitle
import ucf.visor.ui.components.SelectableChip
import ucf.visor.ui.components.SwitchButton
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.ui.icon
import ucf.visor.ui.labelRes
import ucf.visor.ui.viewmodel.VisorViewModel

/**
 * The Phase 1 demo: pick a camera feature, then a script stands in for its
 * answer to a photo. Opened from the Debug menu's Test Suite as one more
 * screen in VISOR (VisorViewModel.phase1), with the usual navigation bar
 * rather than an environment of its own. Which feature's scripts are shown is this screen's own
 * choice, nothing the rest of VISOR depends on. Its title and buttons follow
 * VISOR's text size like every other screen, and each script is shown whole,
 * wrapping, rather than cut off after 25 characters.
 */
@Composable
fun Phase1ModeConfigurationScreen(
    viewModel: VisorViewModel,
    talk: (String, VoiceCategory) -> Unit = { _, _ -> }
) {
    var selected by rememberSaveable { mutableStateOf(CameraCommand.CHECK_HAZARDS) }
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val scriptsArrayRes = when (selected) {
        CameraCommand.CHECK_HAZARDS -> R.array.hazard_awareness_scripts
        CameraCommand.READ_TEXT -> R.array.reading_assistance_scripts
        CameraCommand.IDENTIFY_OBJECTS -> R.array.object_identification_scripts
        CameraCommand.DESCRIBE_SCENE -> R.array.scene_description_scripts
        CameraCommand.ASK_QUESTION -> R.array.question_scripts
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

        // What the buttons below do: this screen opens on its own from the
        // Debug menu, so it says so itself rather than relying on a tester
        // already knowing the Phase 1 flow.
        Text(
            stringResource(R.string.p1_mode_config_description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CameraCommand.entries.forEach { command ->
                val isSelected = command == selected
                SelectableChip(
                    label = stringResource(command.labelRes),
                    selected = isSelected,
                    icon = command.icon,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (!isSelected) {
                            selected = command
                            talk(context.getString(command.labelRes), VoiceCategory.STATUS_UPDATES)
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
                        viewModel.showAnswer(scriptText, selected)
                        talk(scriptText, VoiceCategory.ANSWERS)
                    },
                )
            }
        }
    }
}
