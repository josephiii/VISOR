package ucf.visor.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ucf.visor.ui.components.AutoSizeText
import ucf.visor.ui.components.SelectableChip
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.ui.profile.Severity
import ucf.visor.ui.profile.SpeechRate
import ucf.visor.ui.profile.UserProfile
import ucf.visor.ui.profile.Verbosity
import ucf.visor.ui.profile.VisionType
import ucf.visor.ui.profile.label
import ucf.visor.ui.theme.VisorShapes
import ucf.visor.ui.viewmodel.VisorViewModel


/**
 * VISOR-124 — Voice-first profile creation (first-run onboarding).
 *
 * Design: a guided interview, one question per screen. The app SPEAKS each
 * question (voice-first); every answer is a huge high-contrast button (touch
 * fallback); every step is skippable because UserProfile has safe defaults.
 *
 * @param speak Wire to Android TextToSpeech in MainActivity. No-op default
 *   keeps @Preview working.
 * @param onFinished Receives the completed profile — save + POST to backend.
 */

private enum class Step(val title: String, val spokenPrompt: String) {
    NAME(
        "What should VISOR call you?",
        "Welcome to VISOR. What should I call you? Say your name, or tap Skip."
    ),
    VISION(
        "Which of these describe what you experience?",
        "Which of these describe what you experience? You can pick more than one. Say: center loss, side loss, blurry, light sensitivity, or not sure."
    ),
    DESCRIBE(
        "Tell me about your vision, in your own words",
        "If you'd like, tell me about your vision in your own words. Anything that doesn't fit the boxes. Or tap Skip."
    ),
    SEVERITY(
        "How much does it affect daily life?",
        "How much does your vision affect daily life? Say: a little, a moderate amount, or a lot."
    ),
    SPEECH_RATE(
        "How fast should I talk?",
        "How fast should I talk? Say: slower, normal, or faster."
    ),
    VERBOSITY(
        "How much detail do you want?",
        "When I describe things, how much detail do you want? Say: brief, standard, or detailed."
    ),
    DONE(
        "You're all set!",
        "You're all set. You can change any of this later in Settings, or just ask me."
    ),
}

@Composable
fun ProfileCreationScreen(
    viewModel: VisorViewModel,
    speak: (String) -> Unit = {},
    onFinished: (UserProfile) -> Unit = {},
) {
    var step by remember { mutableStateOf(Step.NAME) }
    var profile by remember { mutableStateOf(UserProfile()) }
    val scrollState = rememberScrollState()

    // Speak each question as it appears — the "voice-first" half of the screen.
    LaunchedEffect(step) { speak(step.spokenPrompt) }

    fun next() {
        val i = step.ordinal
        if (i < Step.entries.lastIndex) step = Step.entries[i + 1] else onFinished(profile)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        LinearProgressIndicator(
            progress = { (step.ordinal + 1) / Step.entries.size.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = step.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )

        when (step) {
            Step.NAME -> BigTextField(profile.displayName, "Your name") {
                profile = profile.copy(displayName = it)
            }

            // Free-text: patients who don't fit the boxes get heard. Voice input later (SpeechRecognizer).
            Step.DESCRIBE -> BigTextField(profile.visionDescription, "In your own words…") {
                profile = profile.copy(visionDescription = it)
            }

            // Multi-select: tapping toggles; "Not sure" clears when a real answer is picked.
            Step.VISION -> VisionType.entries.forEach { type ->
                val selected = type in profile.visionTypes
                BigChoiceButton(type.label(), selected) {
                    val base = profile.visionTypes - VisionType.NOT_SURE
                    profile = profile.copy(
                        visionTypes = (if (selected) base - type else base + type)
                            .ifEmpty { setOf(VisionType.NOT_SURE) }
                    )
                }
            }

            Step.SEVERITY -> Severity.entries.forEach { s ->
                BigChoiceButton(s.label(), profile.severity == s) {
                    profile = profile.copy(severity = s); next()
                }
            }

            Step.SPEECH_RATE -> SpeechRate.entries.forEach { r ->
                BigChoiceButton(r.label(), profile.speechRate == r) {
                    profile = profile.copy(speechRate = r); next()
                }
            }

            Step.VERBOSITY -> Verbosity.entries.forEach { v ->
                BigChoiceButton(v.label(), profile.verbosity == v) {
                    profile = profile.copy(verbosity = v); next()
                }
            }

            Step.DONE -> Text(
                "You can change any of this later in Settings - or just ask.",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = ::next,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp),
            shape = VisorShapes.Control,
        ) {
            AutoSizeText(
                if (step == Step.DONE) "Start using VISOR" else "Continue",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        // Skip is always available — every profile field has a safe default.
        if (step != Step.DONE) {
            TextButton(onClick = ::next, modifier = Modifier.fillMaxWidth()) {
                AutoSizeText("Skip for now", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun BigTextField(
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.headlineSmall,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.headlineSmall) },
        shape = VisorShapes.Control,
    )
    // TODO(VISOR-124): mic button wired to SpeechRecognizer so answers can be spoken.
}

/** One large, high-contrast option row. Min 72dp tall = easy touch target. */
@Composable
private fun BigChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SelectableChip(
        label = label,
        selected = selected,
        modifier = Modifier.fillMaxWidth(),
        minHeight = 72.dp,
        textStyle = MaterialTheme.typography.titleLarge,
        onClick = onClick,
    )
}

// Human-readable labels (also what voice commands should map to).
private fun SpeechRate.label() = when (this) {
    SpeechRate.SLOW -> "Slower"; SpeechRate.NORMAL -> "Normal"; SpeechRate.FAST -> "Faster"
}

private fun Verbosity.label() = when (this) {
    Verbosity.BRIEF -> "Brief"; Verbosity.STANDARD -> "Standard"; Verbosity.DETAILED -> "Detailed"
}