package ucf.visor.ui.phase1

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ucf.visor.R
import ucf.visor.ui.viewmodel.SessionMode
import ucf.visor.ui.viewmodel.VisorViewModel
@Composable
fun Phase1ModeConfigurationScreen(
    viewModel: VisorViewModel,
    talk: (String)->Unit = {}
) {
    val session by viewModel.session.collectAsState()
    val scriptsArrayRes = when (session.mode) {
        SessionMode.HAZARD -> R.array.hazard_awareness_scripts
        SessionMode.SCENE -> R.array.scene_description_scripts
        SessionMode.READER -> R.array.reading_assistance_scripts
    }
    val scripts = stringArrayResource(scriptsArrayRes)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.p1_mode_config_screen_title),
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
            textAlign = TextAlign.Center
        )
        Spacer( modifier = Modifier.height(64.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SessionMode.entries.forEach { mode ->
                val isSelected = mode == session.mode
                ChoiceChip(
                    label = when (mode) {
                        SessionMode.HAZARD -> stringResource(R.string.hazard_mode)
                        SessionMode.SCENE -> stringResource(R.string.scene_mode)
                        SessionMode.READER -> stringResource(R.string.reader_mode)
                    },
                    isSelected = isSelected,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (!isSelected) {
                            viewModel.setMode(mode)
                        }
                    }
                )
            }
        }
        Spacer( modifier = Modifier.height(64.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ){
            scripts.forEachIndexed { index, scriptText ->
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        talk(scriptText)
                    }
                ){
                    Text("Play Script ${index + 1}")
                }
            }
        }
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 64.dp)
            .semantics { selected = isSelected },
        shape = CutCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
        ),
    ) {
        Text(
            label,
            fontSize = 18.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}