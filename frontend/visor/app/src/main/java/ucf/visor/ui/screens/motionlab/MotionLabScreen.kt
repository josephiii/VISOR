package ucf.visor.ui.screens.motionlab

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import ucf.visor.motionlab.LabResult
import ucf.visor.motionlab.LiveStats
import ucf.visor.motionlab.MotionLabUiState
import ucf.visor.motionlab.MotionLabViewModel
import ucf.visor.motionlab.protocol.RunnerState
import ucf.visor.motionlab.protocol.Trial
import ucf.visor.motionlab.protocol.TrialOutcome
import ucf.visor.motionlab.protocol.Trials
import ucf.visor.ui.components.SelectableChip
import ucf.visor.ui.components.SwitchButton
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.ui.theme.VisorShapes
import ucf.visor.wearables.GlassesStatus
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The head-motion lab: a research tool that records the glasses' IMU — and,
 * for the VOR tier, the camera image shift — through MWDAT 1.0's Motion
 * capability, using the same trial battery as the web IMU Lab.
 *
 * Built for testers who may themselves have low vision: everything a trial
 * says on screen is also spoken or sounded, the controls are full-width and
 * tall, the current phase is announced to screen readers as it changes, and
 * the screen stays on for the length of a trial.
 */
@Composable
fun MotionLabScreen(
    wearablesReady: Boolean,
    talk: (String) -> Unit,
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
    onPairGlasses: () -> Unit,
    modifier: Modifier = Modifier,
    // Activity-scoped so a running trial survives a glance at another screen.
    labViewModel: MotionLabViewModel = viewModel(LocalActivity.current as ComponentActivity),
) {
    val state by labViewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val scrollState = rememberScrollState()

    LaunchedEffect(labViewModel, talk) { labViewModel.speak = talk }
    LaunchedEffect(wearablesReady) { if (wearablesReady) labViewModel.observeGlasses() }

    // Keep the phone awake for the whole trial: a raw camera stream pauses when
    // the app leaves the foreground, and a tester should never have to unlock
    // the phone mid-trial to find out what is happening.
    val view = LocalView.current
    DisposableEffect(state.isBusy) {
        view.keepScreenOn = state.isBusy
        onDispose { view.keepScreenOn = false }
    }

    // Backgrounding is written into the recording, the same as the web lab's
    // `visibility_hidden` marks, so the analysis can flag the hole it leaves.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> labViewModel.onAppVisibilityChanged(visible = false)
                Lifecycle.Event.ON_START -> labViewModel.onAppVisibilityChanged(visible = true)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Back during a trial stops the trial rather than leaving it running unseen.
    BackHandler(enabled = state.isRunning) { labViewModel.abort() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Head-motion lab",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "A research tool. It records your glasses' motion sensors, and for the " +
                    "head-motion-versus-camera trials, how far the camera image moves. " +
                    "It uses experimental glasses features, so it only works on test builds.",
            style = MaterialTheme.typography.bodyLarge,
        )

        GlassesCard(
            status = state.glasses,
            registered = state.registered,
            onSpeak = { status -> talk(status.spokenSummary()) },
            onPairGlasses = onPairGlasses,
        )

        if (state.isRunning || state.connecting) {
            RunningPanel(
                state = state,
                onStop = { labViewModel.abort() },
                onBalanceLost = { labViewModel.balanceLost() },
            )
        } else {
            state.message?.let { MessageCard(it) }
            state.lastResult?.let { result ->
                ResultCard(
                    result = result,
                    onShare = { file ->
                        val share =
                            Intent.createChooser(labViewModel.shareIntent(file), "Share recording")
                        activity?.startActivity(share)
                    },
                )
            }
            SetupSection(
                state = state,
                onParticipant = labViewModel::setParticipant,
                onSelect = labViewModel::selectTrial,
            )
            // Short on purpose: SwitchButton shrinks text that does not fit, and
            // the selected trial is named, checked and described just above.
            SwitchButton(
                label = "Start this trial",
                enabled = state.registered && state.glasses?.connected == true,
                onClick = { labViewModel.startSelectedTrial(onRequestWearablesPermission) },
            )
            if (!state.registered || state.glasses?.connected != true) {
                Text(
                    "Connect and register your glasses to start a trial.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            // Listed once per finished trial, not on every recomposition.
            val savedCount = remember(state.lastResult) { labViewModel.savedSessions().size }
            SavedSessionsNote(count = savedCount, folder = MotionLabViewModel.SESSIONS_DIR)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun GlassesCard(
    status: GlassesStatus?,
    registered: Boolean,
    onSpeak: (GlassesStatus) -> Unit,
    onPairGlasses: () -> Unit,
) {
    LabCard {
        Text(
            "Glasses",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        if (!registered || status == null) {
            Text(
                if (!registered) "VISOR is not registered with your glasses yet." else "No glasses found.",
                style = MaterialTheme.typography.bodyLarge,
            )
            SwitchButton(label = "Pair glasses", onClick = onPairGlasses)
            return@LabCard
        }
        Text(
            "${status.model}: ${if (status.connected) "connected" else "not connected"}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        val details = buildList {
            status.batteryPercent?.let { add("Battery $it%" + if (status.charging == true) ", charging" else "") }
            status.worn?.let { add(if (it) "Being worn" else "Not being worn") }
            status.temperatureWord?.let { add("Temperature $it") }
        }
        if (details.isNotEmpty()) {
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
        }
        if (status.isThrottling) {
            Text(
                "The glasses are warm and may slow down. Let them cool before a long trial.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
        SelectableChip(
            label = "Read glasses status aloud",
            selected = false,
            showCheckmark = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onSpeak(status) },
        )
    }
}

@Composable
private fun SetupSection(
    state: MotionLabUiState,
    onParticipant: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    OutlinedTextField(
        value = state.participant,
        onValueChange = onParticipant,
        label = { Text("Participant code") },
        supportingText = { Text("A code such as bench01 — never a name.") },
        singleLine = true,
        shape = VisorShapes.Control,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Done,
        ),
        modifier = Modifier.fillMaxWidth(),
    )

    for ((tier, tierTitle) in Trials.tiers) {
        val trials = Trials.all.filter { it.tier == tier }
        if (trials.isEmpty()) continue
        Text(
            "$tier · $tierTitle",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(top = 8.dp)
                .semantics { heading() },
        )
        for (trial in trials) {
            SelectableChip(
                label = trial.title,
                selected = trial.id == state.selectedTrialId,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSelect(trial.id) },
            )
            if (trial.id == state.selectedTrialId) TrialDetails(trial)
        }
    }
}

@Composable
private fun TrialDetails(trial: Trial) {
    LabCard {
        Text(
            "${formatDuration(trial.durationSec)} · " +
                    (if (trial.camera) "camera and motion sensors" else "motion sensors only") +
                    if (trial.worn) " · worn" else " · glasses set down",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(trial.setup, style = MaterialTheme.typography.bodyLarge)
        Text("Why: ${trial.purpose}", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RunningPanel(state: MotionLabUiState, onStop: () -> Unit, onBalanceLost: () -> Unit) {
    val runner = state.runner
    val phase = when {
        state.connecting -> "Connecting to the glasses…"
        runner is RunnerState.Preparing -> "Get ready"
        runner is RunnerState.WaitingForGlasses -> "Waiting for the glasses…"
        runner is RunnerState.Recording -> "Recording"
        else -> ""
    }
    LabCard {
        // The phase changes a handful of times per trial — announced politely.
        // The ticking numbers below are deliberately not a live region.
        Text(
            phase,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text(state.selectedTrial.title, style = MaterialTheme.typography.titleLarge)
        when (runner) {
            is RunnerState.Preparing -> BigText(
                "${runner.secondsLeft}",
                "Recording starts in ${runner.secondsLeft} seconds"
            )

            is RunnerState.Recording -> {
                BigText(
                    runner.cueLabel ?: formatDuration((runner.elapsedMs / 1000).toInt()),
                    runner.cueLabel ?: "Elapsed",
                )
                Text(
                    "${formatDuration((runner.elapsedMs / 1000).toInt())} of ${formatDuration(runner.trial.durationSec)}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            else -> Unit
        }
        state.selectedTrial.instructions.forEach {
            Text("• $it", style = MaterialTheme.typography.bodyLarge)
        }
        LiveNumbers(state.live, camera = state.selectedTrial.camera)
    }
    // For the tester: the wearer stepped, put a foot down or opened their eyes.
    // That ends a timed hold as a result; Stop trial below is for anything else.
    if (runner is RunnerState.Recording && runner.trial.timedHold) {
        SwitchButton(label = "Balance lost", onClick = onBalanceLost)
    }
    SwitchButton(label = "Stop trial", isDestructive = true, onClick = onStop)
}

@Composable
private fun BigText(text: String, spoken: String) {
    Text(
        text,
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = spoken },
    )
}

@Composable
private fun LiveNumbers(live: LiveStats, camera: Boolean) {
    val lines = buildList {
        add("Motion: ${live.motionHz.roundToInt()} samples a second (${live.motionSamples} total)")
        live.headSpeedDps?.let { add("Head turning: ${it.roundToInt()}° a second") }
        if (camera) {
            add("Camera: ${live.videoFps.roundToInt()} frames a second (${live.analyzedFrames} measured)")
            live.imageShiftPx?.let {
                add(
                    "Image moved ${
                        "%.1f".format(
                            Locale.US,
                            it
                        )
                    } px between frames"
                )
            }
            live.trackingPeak?.let {
                add(
                    "Tracking: " + when {
                        it >= 0.4 -> "good"
                        it >= 0.15 -> "fair"
                        else -> "poor — face a scene with more detail"
                    },
                )
            }
        }
    }
    lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
}

@Composable
private fun ResultCard(result: LabResult, onShare: (java.io.File) -> Unit) {
    LabCard {
        Text(
            when (result.outcome) {
                TrialOutcome.COMPLETED -> "Trial complete"
                TrialOutcome.ABORTED -> "Trial stopped"
                TrialOutcome.FAILED -> "Trial could not finish"
                TrialOutcome.BALANCE_LOST -> "Balance lost: hold time recorded"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Text(result.summary, style = MaterialTheme.typography.bodyLarge)
        result.quickLook?.fit?.takeIf { it.reliable }?.let { fit ->
            Text(
                "Rotation axis ${fit.gyroAxis.uppercase()} → image ${fit.imageAxis.uppercase()} · " +
                        "lag ${fit.lagMs.roundToInt()} ms · r = ${
                            "%.2f".format(
                                Locale.US,
                                fit.correlation
                            )
                        } · " +
                        "${"%.1f".format(Locale.US, fit.pixelsPerDegree)} px/° · " +
                        "residual ${"%.1f".format(Locale.US, fit.residualDps)}°/s",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        result.file?.let { file ->
            SwitchButton(label = "Share recording", onClick = { onShare(file) })
        }
    }
}

@Composable
private fun MessageCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = VisorShapes.Control,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(16.dp)
                .semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
}

@Composable
private fun SavedSessionsNote(count: Int, folder: String) {
    if (count == 0) return
    Text(
        "$count recording${if (count == 1) "" else "s"} saved on this phone " +
                "(Android/data/…/files/$folder). Share each one after its trial, or copy them " +
                "with adb pull for analysis/analyze.py.",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun LabCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = VisorShapes.Control,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}

private fun formatDuration(seconds: Int): String =
    if (seconds >= 60) "%d:%02d".format(Locale.US, seconds / 60, seconds % 60) else "$seconds s"
