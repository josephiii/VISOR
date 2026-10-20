package ucf.visor.ui.screens.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ucf.visor.R
import ucf.visor.data.Severity
import ucf.visor.data.SpeechRates
import ucf.visor.data.UserProfile
import ucf.visor.data.Verbosity
import ucf.visor.data.VisionType
import ucf.visor.data.VoiceCategory
import ucf.visor.data.label
import ucf.visor.ui.components.SelectableChip
import ucf.visor.ui.components.StepIndicator
import ucf.visor.ui.components.SwitchButton
import ucf.visor.ui.components.VisorSlider
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.ui.theme.VisorShapes
import ucf.visor.ui.viewmodel.VisorViewModel
import ucf.visor.ui.voice.VoiceNavigationController


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

// Each step's question, on screen and spoken, from strings.xml (step_*).
private enum class Step(@StringRes val title: Int, @StringRes val spokenPrompt: Int) {
    NAME(R.string.step_name_title, R.string.step_name_spoken_prompt),
    VOICE_NAV(R.string.step_voice_nav_title, R.string.step_voice_nav_spoken_prompt),
    VISION(R.string.step_vision_title, R.string.step_vision_spoken_prompt),
    DESCRIBE(R.string.step_describe_title, R.string.step_describe_spoken_prompt),
    SEVERITY(R.string.step_severity_title, R.string.step_severity_spoken_prompt),
    SPEECH_RATE(R.string.step_speech_rate_title, R.string.step_speech_rate_spoken_prompt),
    VERBOSITY(R.string.step_verbosity_title, R.string.step_verbosity_spoken_prompt),
    DONE(R.string.step_done_title, R.string.step_done_spoken_prompt),
}

@Composable
fun ProfileCreationScreen(
    viewModel: VisorViewModel,
    speak: (String, VoiceCategory) -> Unit = { _, _ -> },
    voiceNav: VoiceNavigationController? = null,
    onFinished: (UserProfile) -> Unit = {},
) {
    var step by remember { mutableStateOf(Step.NAME) }
    var profile by remember { mutableStateOf(viewModel.userProfile.value) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    fun next() {
        voiceNav?.cancelCapture() // on touch tap
        val i = step.ordinal
        if (i < Step.entries.lastIndex) step = Step.entries[i + 1] else onFinished(profile)
    }

    // VOICE NAVIGATION
    LaunchedEffect(step) {
        if (!profile.voiceNavigationEnabled) return@LaunchedEffect
        // VISOR's half of a voice conversation, like its replies to commands.
        speak(context.getString(step.spokenPrompt), VoiceCategory.VOICE_COMMANDS)
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
            .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline)
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState)
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        StepIndicator(step = step.ordinal + 1, of = Step.entries.size)

        Text(
            text = stringResource(step.title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )

        when (step) {
            Step.NAME -> BigTextField(profile.displayName, stringResource(R.string.settings_name_label)) {
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

            Step.DESCRIBE -> BigTextField(
                profile.visionDescription,
                stringResource(R.string.settings_vision_description_label),
            ) {
                profile = profile.copy(visionDescription = it)
            }

            Step.VISION -> VisionType.entries.forEach { type ->
                val selected = type in profile.visionTypes
                BigChoiceButton(type.label(), selected, multiSelect = true) {
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
                VisorSlider(
                    value = profile.speechRate,
                    onValueChange = { position ->
                        val rate = SpeechRates.snap(position)
                        if (rate != profile.speechRate) profile = profile.copy(speechRate = rate)
                    },
                    valueRange = SpeechRates.Min..SpeechRates.Max,
                    steps = SpeechRates.SliderSteps,
                    label = stringResource(R.string.settings_speech_rate_label),
                    valueDescription = SpeechRates.spokenLabel(profile.speechRate),
                    decreaseDescription = stringResource(R.string.settings_speech_rate_slower_button),
                    increaseDescription = stringResource(R.string.settings_speech_rate_faster_button),
                )
                SliderEnds(
                    start = stringResource(R.string.settings_speech_rate_slower),
                    end = stringResource(R.string.settings_speech_rate_faster),
                )
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

        SwitchButton(
            label = stringResource(if (step == Step.DONE) R.string.onboarding_start else R.string.onboarding_continue),
            onClick = ::next,
            textStyle = MaterialTheme.typography.titleLarge,
            minHeight = 72.dp,
        )

        // Skip is always available — every profile field has a safe default.
        // A full button, as easy to find and press as Continue, not a line of text.
        if (step != Step.DONE) {
            SwitchButton(
                label = stringResource(R.string.onboarding_skip),
                onClick = ::next,
                outlined = true,
                textStyle = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/**
 * A large text field with a real label, which stays above what is typed. It
 * had only a placeholder, gone as soon as there was a name in the field.
 */
@Composable
private fun BigTextField(
    value: String,
    label: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.headlineSmall,
        label = { Text(label) },
        shape = VisorShapes.Control,
    )
}

/**
 * One large, high-contrast option row. Min 72dp tall = easy touch target.
 * [multiSelect] for a question whose answers can be picked together.
 */
@Composable
private fun BigChoiceButton(
    label: String,
    selected: Boolean,
    multiSelect: Boolean = false,
    onClick: () -> Unit,
) {
    SelectableChip(
        label = label,
        selected = selected,
        multiSelect = multiSelect,
        modifier = Modifier.fillMaxWidth(),
        minHeight = 72.dp,
        textStyle = MaterialTheme.typography.titleLarge,
        onClick = onClick,
    )
}

private fun Verbosity.label() = when (this) {
    Verbosity.BRIEF -> "Brief"; Verbosity.STANDARD -> "Standard"; Verbosity.DETAILED -> "Detailed"
}