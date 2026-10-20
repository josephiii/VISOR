package ucf.visor.ui.screens.help

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ucf.visor.R
import ucf.visor.data.VoiceCategory
import ucf.visor.ui.components.ScreenTitle
import ucf.visor.ui.components.SwitchButton
import ucf.visor.ui.components.scrollIndicator

private data class HelpTopic(@StringRes val title: Int, @StringRes val description: Int)

private val helpTopics = listOf(
    HelpTopic(R.string.help_session_title, R.string.help_session_body),
    HelpTopic(R.string.help_pairing_title, R.string.help_pairing_body),
    HelpTopic(R.string.help_voice_launch_title, R.string.help_voice_launch_body),
    HelpTopic(R.string.help_camera_title, R.string.help_camera_body),
    HelpTopic(R.string.help_voice_nav_title, R.string.help_voice_nav_body),
    HelpTopic(R.string.help_profile_title, R.string.help_profile_body),
    HelpTopic(R.string.help_personalize_title, R.string.help_personalize_body),
)

/**
 * How VISOR works, a topic at a time. Each topic is a heading a screen reader
 * can jump to, in short paragraphs, with a Read aloud button that says it at
 * the Answers volume.
 */
@Composable
fun HelpScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    talk: (String, VoiceCategory) -> Unit = { _, _ -> },
) {
    val scrollState = rememberScrollState()
    // The system's Back goes where the Back button does.
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline)
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState)
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SwitchButton(
            label = stringResource(R.string.back),
            outlined = true,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onBack,
        )

        ScreenTitle(stringResource(R.string.help_title))

        helpTopics.forEach { topic ->
            val title = stringResource(topic.title)
            val body = stringResource(topic.description)
            val readAloudName = stringResource(R.string.help_read_aloud_named, title)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(body, style = MaterialTheme.typography.bodyLarge)
                SwitchButton(
                    label = stringResource(R.string.help_read_aloud),
                    outlined = true,
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    onClick = { talk("$title. $body", VoiceCategory.ANSWERS) },
                    // Seven buttons say "Read aloud"; each is named for its topic.
                    modifier = Modifier.semantics { contentDescription = readAloudName },
                )
            }
        }
    }
}
