package ucf.visor.ui.screens.profile

import android.content.Context
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
import androidx.compose.runtime.mutableIntStateOf
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
import ucf.visor.data.ThemeMode
import ucf.visor.data.UserProfile
import ucf.visor.data.Verbosity
import ucf.visor.data.VisionPlan
import ucf.visor.data.VisionType
import ucf.visor.data.VoiceCategory
import ucf.visor.data.label
import ucf.visor.ui.components.SelectableChip
import ucf.visor.ui.components.StepIndicator
import ucf.visor.ui.components.SwitchButton
import ucf.visor.ui.components.SwitchChip
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

    /**
     * Only when their own words name an eye condition whose usual kind of
     * vision loss they didn't pick (VisionPlan.suggestedTypes): offers to add
     * it. Its spoken prompt is built from what they said (see [spokenPrompt]).
     */
    CONFIRM_TYPES(R.string.step_confirm_types_title, R.string.step_confirm_types_spoken_prompt),
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

    // What their own words suggest adding (CONFIRM_TYPES), worked out once as
    // they leave DESCRIBE, so the step doesn't vanish as they answer it.
    var suggestedTypes by remember { mutableStateOf<Map<VisionType, String>>(emptyMap()) }
    val steps = Step.entries.filter { it != Step.CONFIRM_TYPES || suggestedTypes.isNotEmpty() }

    // How VISOR will fit their vision, from the answers so far (data/VisionPlan).
    val plan = VisionPlan.from(profile)

    // The look VISOR suggests for contrast and light sensitivity, offered on
    // the last step and on until they turn it off there.
    var useSuggestedLook by remember { mutableStateOf(true) }
    val offersLook = plan.suggestsDarkHighContrast

    // Bumped to ask the last step's question again after a "no" to the look.
    var reasked by remember { mutableIntStateOf(0) }

    fun finish() {
        onFinished(
            if (offersLook && useSuggestedLook) profile.copy(themeMode = ThemeMode.DARK, appHighContrast = true)
            else profile
        )
    }

    fun next() {
        voiceNav?.cancelCapture() // on touch tap
        if (step == Step.DESCRIBE) {
            suggestedTypes = VisionPlan.suggestedTypes(profile.visionDescription, profile.visionTypes)
        }
        val visible = Step.entries.filter { it != Step.CONFIRM_TYPES || suggestedTypes.isNotEmpty() }
        val i = visible.indexOf(step)
        if (i < visible.lastIndex) step = visible[i + 1] else finish()
        // Detail starts at the level suggested for them, unless already changed
        // from the default; Skip then keeps the suggestion.
        if (step == Step.VERBOSITY && profile.verbosity == UserProfile().verbosity) {
            profile = profile.copy(verbosity = VisionPlan.from(profile).recommendedVerbosity)
        }
    }

    fun addSuggestedTypes() {
        profile = profile.copy(visionTypes = profile.visionTypes - VisionType.NOT_SURE + suggestedTypes.keys)
    }

    // VOICE NAVIGATION
    LaunchedEffect(step, reasked) {
        if (!profile.voiceNavigationEnabled) return@LaunchedEffect
        // VISOR's half of a voice conversation, like its replies to commands.
        speak(
            if (reasked > 0 && step == Step.DONE) context.getString(R.string.step_done_look_declined_spoken)
            else spokenPrompt(context, step, profile, suggestedTypes, offersLook && useSuggestedLook),
            VoiceCategory.VOICE_COMMANDS,
        )
        val askedStep = step
        voiceNav?.captureUtterance { text ->
            val heard = text?.trim()
            if (heard.isNullOrEmpty()) return@captureUtterance
            // "Skip", "next"... on any question, as the Skip button: so a name
            // is never saved as "Skip". See OnboardingAnswers.
            if (askedStep != Step.DONE && OnboardingAnswers.isSkip(heard)) {
                next()
                return@captureUtterance
            }
            when (askedStep) {
                Step.NAME -> OnboardingAnswers.name(heard)?.let {
                    profile = profile.copy(displayName = it); next()
                }

                Step.VOICE_NAV -> OnboardingAnswers.yesNo(heard)?.let {
                    profile = profile.copy(voiceNavigationEnabled = it); next()
                }

                Step.VISION -> OnboardingAnswers.visionTypes(heard)?.let {
                    profile = profile.copy(visionTypes = it); next()
                }

                Step.DESCRIBE -> {
                    profile = profile.copy(visionDescription = heard); next()
                }

                Step.CONFIRM_TYPES -> OnboardingAnswers.yesNo(heard)?.let { add ->
                    if (add) addSuggestedTypes()
                    next()
                }

                Step.SEVERITY -> OnboardingAnswers.severity(heard)?.let {
                    profile = profile.copy(severity = it); next()
                }

                Step.SPEECH_RATE -> OnboardingAnswers.speechRate(heard)?.let {
                    profile = profile.copy(speechRate = it); next()
                }

                Step.VERBOSITY -> OnboardingAnswers.verbosity(heard)?.let {
                    profile = profile.copy(verbosity = it); next()
                }

                // "No" turns the suggested look off; "start" finishes. Both
                // can come at once ("no, start"); a "no" alone asks again.
                Step.DONE -> {
                    val declinedLook =
                        offersLook && useSuggestedLook && OnboardingAnswers.yesNo(heard) == false
                    if (declinedLook) useSuggestedLook = false
                    when {
                        OnboardingAnswers.isStart(heard) -> next()
                        declinedLook -> reasked++
                    }
                }
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
        StepIndicator(step = steps.indexOf(step) + 1, of = steps.size)

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

            Step.DESCRIBE -> {
                BigTextField(
                    profile.visionDescription,
                    stringResource(R.string.settings_vision_description_label),
                ) {
                    profile = profile.copy(visionDescription = it)
                }
                // Where their words go, said before they write them.
                Text(
                    stringResource(R.string.step_describe_sharing_note),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Step.CONFIRM_TYPES -> {
                val condition = suggestedTypes.values.first()
                Text(
                    stringResource(R.string.step_confirm_types_reason, condition),
                    style = MaterialTheme.typography.titleMedium,
                )
                suggestedTypes.keys.forEach { type ->
                    Text(
                        "• " + type.label(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                BigChoiceButton(stringResource(R.string.step_confirm_types_yes), selected = false) {
                    addSuggestedTypes(); next()
                }
                BigChoiceButton(stringResource(R.string.step_confirm_types_no), selected = false) {
                    next()
                }
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
                val label =
                    if (v == plan.recommendedVerbosity) stringResource(R.string.step_verbosity_recommended, v.label())
                    else v.label()
                BigChoiceButton(label, profile.verbosity == v) {
                    profile = profile.copy(verbosity = v); next()
                }
            }

            Step.DONE -> {
                if (!plan.isUntailored) {
                    Text(
                        stringResource(R.string.step_done_fits_you),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    VisionFitSummary(plan)
                }
                if (offersLook) {
                    SwitchChip(
                        title = stringResource(R.string.step_done_look),
                        on = useSuggestedLook,
                        onToggle = { useSuggestedLook = !useSuggestedLook },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "You can change any of this later in Settings - or just ask.",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
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
/**
 * What setup says as it asks [step]: the step's own prompt, made from what was
 * said before it where it needs that. Their own words name a condition for
 * CONFIRM_TYPES; Detail adds the level suggested for them; the last step first
 * says how VISOR will fit them, and offers the suggested look when [offerLook].
 */
private fun spokenPrompt(
    context: Context,
    step: Step,
    profile: UserProfile,
    suggestedTypes: Map<VisionType, String>,
    offerLook: Boolean,
): String {
    val prompt = context.getString(step.spokenPrompt)
    return when (step) {
        Step.CONFIRM_TYPES -> {
            val kinds = suggestedTypes.keys.map { it.spokenLabel() }
            val joined = if (kinds.size < 2) kinds.joinToString() else
                kinds.dropLast(1).joinToString(", ") + " " +
                    context.getString(R.string.step_confirm_types_and) + " " + kinds.last()
            context.getString(R.string.step_confirm_types_spoken_prompt, suggestedTypes.values.first(), joined)
        }

        Step.VERBOSITY -> prompt + " " + context.getString(
            R.string.step_verbosity_recommended_spoken,
            VisionPlan.from(profile).recommendedVerbosity.label().lowercase(),
        )

        Step.DONE -> {
            val plan = VisionPlan.from(profile)
            if (plan.isUntailored) return prompt
            buildList {
                add(context.getString(R.string.step_done_fits_you_spoken))
                addAll(visionFitLines(context, plan))
                if (offerLook) add(context.getString(R.string.step_done_look_spoken))
                add(prompt)
            }.joinToString(" ")
        }

        else -> prompt
    }
}
