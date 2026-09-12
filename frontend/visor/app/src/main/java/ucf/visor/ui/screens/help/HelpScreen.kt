package ucf.visor.ui.screens.help

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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ucf.visor.ui.components.VisorButton
import ucf.visor.ui.components.scrollIndicator

private data class HelpTopic(val title: String, val description: String)

// Grounded in what VISOR's screens actually do today — not a list of voice
// commands the backend doesn't wire up yet, but a plain-language reference for
// what each part of the app is for, since a voice-first assistive app is
// exactly the kind of app that benefits from being explainable in one place
// rather than only discoverable by trial and error.
private val helpTopics = listOf(
    HelpTopic(
        "Starting a session",
        "On the Home screen, the large center button starts and ends a VISOR session. " +
            "While a session is active, VISOR describes what your glasses see.",
    ),
    HelpTopic(
        "Pairing your glasses",
        "Use the Pair tab to connect your Meta glasses through the Meta AI app. " +
            "You'll need to pair before you can start streaming or capture photos.",
    ),
    HelpTopic(
        "Capturing and describing a photo",
        "While streaming, the camera button takes a photo and shows a description of it. " +
            "Use this any time you want VISOR to describe something specific.",
    ),
    HelpTopic(
        "Setting up your profile",
        "The first time you open VISOR, a short set of questions about your vision, " +
            "your name, and how you'd like VISOR to talk builds your profile. Every question " +
            "is skippable — you can always fill it in later.",
    ),
    HelpTopic(
        "Personalizing VISOR",
        "In Settings, you can change how fast VISOR talks, how much detail it gives, " +
            "your text size, and turn on High Contrast mode. Changes apply immediately.",
    ),
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
            .verticalScroll(scrollState)
            .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        VisorButton(text = "Back", width = 0.4f, onClick = onBack)

        Text(
            "Help & how VISOR works",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )

        helpTopics.forEach { topic ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    topic.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(topic.description, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
