package ucf.visor.ui.screens.help

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ucf.visor.R
import ucf.visor.ui.components.VisorButton
import ucf.visor.ui.components.scrollIndicator

private data class HelpTopic(@StringRes val title: Int, @StringRes val description: Int)

private val helpTopics = listOf(
    HelpTopic(R.string.help_session_title, R.string.help_session_body),
    HelpTopic(R.string.help_pairing_title, R.string.help_pairing_body),
    HelpTopic(R.string.help_voice_launch_title, R.string.help_voice_launch_body),
    HelpTopic(R.string.help_camera_title, R.string.help_camera_body),
    HelpTopic(R.string.help_profile_title, R.string.help_profile_body),
    HelpTopic(R.string.help_personalize_title, R.string.help_personalize_body),
)

@Composable
fun HelpScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .scrollIndicator(scrollState, MaterialTheme.colorScheme.tertiary)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        VisorButton(text = stringResource(R.string.back), width = 0.4f, onClick = onBack)

        Text(
            stringResource(R.string.help_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )

        helpTopics.forEach { topic ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(topic.title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(topic.description), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
