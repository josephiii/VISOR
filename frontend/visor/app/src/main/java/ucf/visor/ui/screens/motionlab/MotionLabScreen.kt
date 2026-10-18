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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import ucf.visor.R
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
    val shareTitle = stringResource(R.string.motion_lab_share_recording)

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
            .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline)
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState)
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.motion_lab_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.motion_lab_intro),
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
                            Intent.createChooser(labViewModel.shareIntent(file), shareTitle)
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
                label = stringResource(R.string.motion_lab_start_trial),
                enabled = state.registered && state.glasses?.connected == true,
                onClick = { labViewModel.startSelectedTrial(onRequestWearablesPermission) },
            )
            if (!state.registered || state.glasses?.connected != true) {
                Text(
                    stringResource(R.string.motion_lab_connect_to_start),
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
            stringResource(R.string.motion_lab_glasses_heading),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        if (!registered || status == null) {
            Text(
                stringResource(
                    if (!registered) R.string.motion_lab_not_registered else R.string.motion_lab_no_glasses
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            SwitchButton(label = stringResource(R.string.motion_lab_pair_glasses), onClick = onPairGlasses)
            return@LabCard
        }
        Text(
            stringResource(
                if (status.connected) R.string.motion_lab_glasses_connected
                else R.string.motion_lab_glasses_not_connected,
                status.model,
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        val details = buildList {
            status.batteryPercent?.let {
                add(
                    stringResource(
                        if (status.charging == true) R.string.motion_lab_battery_charging
                        else R.string.motion_lab_battery,
                        it,
                    )
                )
            }
            status.worn?.let {
                add(stringResource(if (it) R.string.motion_lab_worn else R.string.motion_lab_not_worn))
            }
            status.temperatureWord?.let { add(stringResource(R.string.motion_lab_temperature, it)) }
        }
        if (details.isNotEmpty()) {
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
        }
        if (status.isThrottling) {
            Text(
                stringResource(R.string.motion_lab_throttling),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
        SwitchButton(label = stringResource(R.string.motion_lab_read_status), onClick = { onSpeak(status) })
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
        label = { Text(stringResource(R.string.motion_lab_participant_label)) },
        supportingText = { Text(stringResource(R.string.motion_lab_participant_hint)) },
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
            listOf(
                formatDuration(trial.durationSec),
                stringResource(
                    if (trial.camera) R.string.motion_lab_trial_camera else R.string.motion_lab_trial_motion_only
                ),
                stringResource(if (trial.worn) R.string.motion_lab_trial_worn else R.string.motion_lab_trial_set_down),
            ).joinToString(" · "),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(trial.setup, style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.motion_lab_trial_why, trial.purpose), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RunningPanel(state: MotionLabUiState, onStop: () -> Unit, onBalanceLost: () -> Unit) {
    val runner = state.runner
    val phase = when {
        state.connecting -> stringResource(R.string.motion_lab_phase_connecting)
        runner is RunnerState.Preparing -> stringResource(R.string.motion_lab_phase_get_ready)
        runner is RunnerState.WaitingForGlasses -> stringResource(R.string.motion_lab_phase_waiting)
        runner is RunnerState.Recording -> stringResource(R.string.motion_lab_phase_recording)
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
                pluralStringResource(R.plurals.motion_lab_countdown_spoken, runner.secondsLeft, runner.secondsLeft),
            )

            is RunnerState.Recording -> {
                BigText(
                    runner.cueLabel ?: formatDuration((runner.elapsedMs / 1000).toInt()),
                    runner.cueLabel ?: stringResource(R.string.motion_lab_elapsed),
                )
                Text(
                    stringResource(
                        R.string.motion_lab_elapsed_of_total,
                        formatDuration((runner.elapsedMs / 1000).toInt()),
                        formatDuration(runner.trial.durationSec),
                    ),
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
        SwitchButton(label = stringResource(R.string.motion_lab_balance_lost), onClick = onBalanceLost)
    }
    SwitchButton(label = stringResource(R.string.motion_lab_stop_trial), isDestructive = true, onClick = onStop)
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
        val motionHz = live.motionHz.roundToInt()
        add(pluralStringResource(R.plurals.motion_lab_live_motion, motionHz, motionHz, live.motionSamples))
        live.headSpeedDps?.let { add(stringResource(R.string.motion_lab_live_head_speed, it.roundToInt())) }
        if (camera) {
            val videoFps = live.videoFps.roundToInt()
            add(pluralStringResource(R.plurals.motion_lab_live_camera, videoFps, videoFps, live.analyzedFrames))
            live.imageShiftPx?.let {
                add(stringResource(R.string.motion_lab_live_image_shift, "%.1f".format(Locale.US, it)))
            }
            live.trackingPeak?.let {
                add(
                    stringResource(
                        when {
                            it >= 0.4 -> R.string.motion_lab_tracking_good
                            it >= 0.15 -> R.string.motion_lab_tracking_fair
                            else -> R.string.motion_lab_tracking_poor
                        }
                    )
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
            stringResource(
                when (result.outcome) {
                    TrialOutcome.COMPLETED -> R.string.motion_lab_result_completed
                    TrialOutcome.ABORTED -> R.string.motion_lab_result_aborted
                    TrialOutcome.FAILED -> R.string.motion_lab_result_failed
                    TrialOutcome.BALANCE_LOST -> R.string.motion_lab_result_balance_lost
                }
            ),
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
                stringResource(
                    R.string.motion_lab_fit,
                    fit.gyroAxis.uppercase(),
                    fit.imageAxis.uppercase(),
                    fit.lagMs.roundToInt(),
                    "%.2f".format(Locale.US, fit.correlation),
                    "%.1f".format(Locale.US, fit.pixelsPerDegree),
                    "%.1f".format(Locale.US, fit.residualDps),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        result.file?.let { file ->
            SwitchButton(label = stringResource(R.string.motion_lab_share_recording), onClick = { onShare(file) })
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
        pluralStringResource(R.plurals.motion_lab_saved_recordings, count, count, folder),
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
