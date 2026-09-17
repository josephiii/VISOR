package ucf.visor.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ucf.visor.ui.components.AutoSizeText
import ucf.visor.ui.components.SelectableChip
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.ui.profile.Severity
import ucf.visor.ui.profile.SpeechRates
import ucf.visor.ui.profile.UserProfile
import ucf.visor.ui.profile.Verbosity
import ucf.visor.ui.profile.VisionType
import ucf.visor.ui.profile.label
import ucf.visor.ui.theme.VisorShapes
import ucf.visor.ui.viewmodel.VisorViewModel
import ucf.visor.ui.voice.VoiceNavigationController
import kotlin.math.roundToInt


/**
 * Voice-first profile creation (first-run onboarding).
 *
 * Design: a guided interview, one question per screen. The app SPEAKS each
 * question (voice-first); every answer is a huge high-contrast button (touch
 * fallback); every step is skippable because UserProfile has safe defaults.
 *
 * @param speak Wire to Android TextToSpeech in MainActivity. No-op default
 *   keeps @Preview working.
 * @param voiceNav When present, each question also listens for a spoken answer
 *   right after asking it (no "VISOR GO" needed here — the whole screen is
 *   already a voice-first conversation by design). Null keeps @Preview working
 *   and disables voice answers without disabling the touch fallback.
 * @param onFinished Receives the completed profile — save + POST to backend.
 */

private enum class Step(val title: String, val spokenPrompt: String) {
    NAME(
        "What should VISOR call you?",
        "Welcome to VISOR. What should I call you? Say your name, or tap Skip."
    ),
    VOICE_NAV(
        "Navigate VISOR by voice?",
        "You can navigate VISOR by voice, any time, by saying \"VISOR GO\" followed by " +
                "a command, like \"open settings\" or \"go home\". It stays off unless you " +
                "turn it on. Say yes to turn it on, or no to leave it off — you can always " +
                "change this later in Settings."
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
    voiceNav: VoiceNavigationController? = null,
    onFinished: (UserProfile) -> Unit = {},
) {
    var step by remember { mutableStateOf(Step.NAME) }
    var profile by remember { mutableStateOf(viewModel.userProfile.value) }
    val scrollState = rememberScrollState()

    fun next() {
        voiceNav?.cancelCapture() // on touch tap
        val i = step.ordinal
        if (i < Step.entries.lastIndex) step = Step.entries[i + 1] else onFinished(profile)
    }

    // VOICE NAVIGATION
    LaunchedEffect(step) {
        // Speaking is gated on the same preference as listening. Onboarding used
        // to read every prompt aloud whether or not the user had asked for voice
        // at all, which meant a user who deliberately left it off on the title
        // screen still got talked at the moment they signed up.
        //
        // Read fresh on each step rather than captured once: the VOICE_NAV step
        // below can turn it on mid-wizard, and the steps after it should start
        // speaking when it does.
        if (!profile.voiceNavigationEnabled) return@LaunchedEffect
        speak(step.spokenPrompt)
        val askedStep = step
        voiceNav?.captureUtterance { text ->
            val heard = text?.trim()?.lowercase()
            if (heard.isNullOrEmpty()) return@captureUtterance
            when (askedStep) {
                Step.NAME -> {
                    profile = profile.copy(displayName = text.trim())
                    next()
                }

                Step.VOICE_NAV -> when {
                    heard.contains("no") -> {
                        profile = profile.copy(voiceNavigationEnabled = false); next()
                    }

                    heard.contains("yes") -> {
                        profile = profile.copy(voiceNavigationEnabled = true); next()
                    }

                    else -> Unit
                }

                Step.VISION -> {
                    val match = when {
                        heard.contains("center") -> VisionType.CENTRAL_LOSS
                        heard.contains("side") || heard.contains("peripheral") -> VisionType.PERIPHERAL_LOSS
                        heard.contains("blur") -> VisionType.BLUR_LOW_ACUITY
                        heard.contains("light") || heard.contains("contrast") -> VisionType.CONTRAST_LIGHT
                        heard.contains("not sure") -> VisionType.NOT_SURE
                        else -> null
                    }
                    if (match != null) {
                        profile = profile.copy(visionTypes = setOf(match))
                        next()
                    }
                }

                Step.DESCRIBE -> {
                    profile = profile.copy(visionDescription = text.trim())
                    next()
                }

                Step.SEVERITY -> {
                    val match = when {
                        heard.contains("little") -> Severity.MILD
                        heard.contains("moderate") -> Severity.MODERATE
                        heard.contains("lot") -> Severity.SEVERE
                        else -> null
                    }
                    if (match != null) {
                        profile = profile.copy(severity = match); next()
                    }
                }

                Step.SPEECH_RATE -> {
                    // Onboarding stays on the three presets: a user meeting
                    // VISOR for the first time is answering a spoken question,
                    // not tuning a multiplier. The slider in Settings is where
                    // the full range lives.
                    val match = when {
                        heard.contains("slow") -> SpeechRates.Slow
                        heard.contains("fast") -> SpeechRates.Fast
                        heard.contains("normal") -> SpeechRates.Normal
                        else -> null
                    }
                    if (match != null) {
                        profile = profile.copy(speechRate = match); next()
                    }
                }

                Step.VERBOSITY -> {
                    val match = when {
                        heard.contains("brief") -> Verbosity.BRIEF
                        heard.contains("detail") -> Verbosity.DETAILED
                        heard.contains("standard") -> Verbosity.STANDARD
                        else -> null
                    }
                    if (match != null) {
                        profile = profile.copy(verbosity = match); next()
                    }
                }

                Step.DONE -> if (heard.contains("start")) next()
            }
        }
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

            Step.VOICE_NAV -> listOf(true, false).forEach { enabled ->
                BigChoiceButton(
                    if (enabled) "Yes, keep voice navigation on" else "No, turn it off",
                    profile.voiceNavigationEnabled == enabled,
                ) {
                    profile = profile.copy(voiceNavigationEnabled = enabled); next()
                }
            }

            Step.DESCRIBE -> BigTextField(profile.visionDescription, "In your own words…") {
                profile = profile.copy(visionDescription = it)
            }

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

            Step.SPEECH_RATE -> {
                Text(
                    text = SpeechRates.label(profile.speechRate),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Slider(
                    value = profile.speechRate,
                    onValueChange = { position ->
                        val rate = SpeechRates.snap(position)
                        if (rate != profile.speechRate) profile = profile.copy(speechRate = rate)
                    },
                    valueRange = SpeechRates.Min..SpeechRates.Max,
                    steps = SpeechRates.SliderSteps,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Speech rate: ${SpeechRates.spokenLabel(profile.speechRate)}"
                        },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Slower", style = MaterialTheme.typography.bodyMedium)
                    Text("Faster", style = MaterialTheme.typography.bodyMedium)
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

private fun Verbosity.label() = when (this) {
    Verbosity.BRIEF -> "Brief"; Verbosity.STANDARD -> "Standard"; Verbosity.DETAILED -> "Detailed"
}