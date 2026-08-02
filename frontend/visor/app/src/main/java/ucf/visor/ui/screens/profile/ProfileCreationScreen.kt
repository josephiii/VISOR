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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ucf.visor.ui.profile.Severity
import ucf.visor.ui.profile.SpeechRate
import ucf.visor.ui.profile.UserProfile
import ucf.visor.ui.profile.Verbosity
import ucf.visor.ui.profile.VisionType
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
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        LinearProgressIndicator(
            progress = { (step.ordinal + 1) / Step.entries.size.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = step.title,
            fontSize = 34.sp,
            lineHeight = 42.sp,
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
                fontSize = 22.sp,
                lineHeight = 30.sp,
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = ::next,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp),
            shape = CutCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
            ),
        ) {
            Text(
                if (step == Step.DONE) "Start using VISOR" else "Continue",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Skip is always available — every profile field has a safe default.
        if (step != Step.DONE) {
            TextButton(onClick = ::next, modifier = Modifier.fillMaxWidth()) {
                Text("Skip for now", fontSize = 20.sp)
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
        textStyle = TextStyle(fontSize = 26.sp),
        placeholder = { Text(placeholder, fontSize = 26.sp) },
        colors = OutlinedTextFieldDefaults.colors(
        ),
        shape = CutCornerShape(4.dp)
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
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        shape = CutCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            if (selected) Text("✓", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// Human-readable labels (also what voice commands should map to).
private fun VisionType.label() = when (this) {
    VisionType.CENTRAL_LOSS -> "Trouble seeing the center"
    VisionType.PERIPHERAL_LOSS -> "Trouble seeing the sides"
    VisionType.BLUR_LOW_ACUITY -> "Everything is blurry"
    VisionType.CONTRAST_LIGHT -> "Contrast / light sensitivity"
    VisionType.NOT_SURE -> "Not sure"
}

private fun Severity.label() = when (this) {
    Severity.MILD -> "A little"; Severity.MODERATE -> "A moderate amount"; Severity.SEVERE -> "A lot"
}

private fun SpeechRate.label() = when (this) {
    SpeechRate.SLOW -> "Slower"; SpeechRate.NORMAL -> "Normal"; SpeechRate.FAST -> "Faster"
}

private fun Verbosity.label() = when (this) {
    Verbosity.BRIEF -> "Brief"; Verbosity.STANDARD -> "Standard"; Verbosity.DETAILED -> "Detailed"
}