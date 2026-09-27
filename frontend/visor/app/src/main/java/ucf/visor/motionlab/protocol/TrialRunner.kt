package ucf.visor.motionlab.protocol

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TrialOutcome { COMPLETED, ABORTED, FAILED }

/** Where a trial is. The recording screen renders exactly this. */
sealed interface RunnerState {
    data object Idle : RunnerState

    /** The countdown before recording; nothing is recorded yet. */
    data class Preparing(val trial: Trial, val secondsLeft: Int) : RunnerState

    /** The countdown is over but the glasses' streams are not up yet. */
    data class WaitingForGlasses(val trial: Trial) : RunnerState

    data class Recording(
        val trial: Trial,
        val elapsedMs: Long,
        val cueLabel: String?,
        val cueIndex: Int,
    ) : RunnerState

    data class Finished(
        val trial: Trial,
        val outcome: TrialOutcome,
        val reason: String?,
        val recorded: Boolean,
    ) : RunnerState
}

/** What the runner needs from whoever owns the glasses and the recording. */
interface TrialHost {
    /**
     * Suspends until the glasses' streams for [trial] are delivering data and
     * returns null — or returns why they cannot, and the trial fails without
     * recording anything.
     */
    suspend fun awaitStreams(trial: Trial): String?

    /** Recording starts now: open the session and stamp its start time. */
    fun beginRecording(trial: Trial)

    fun mark(label: String, extra: Map<String, Any?>? = null)

    /**
     * The trial is over; release the glasses and, when [recorded], save the
     * session. [recorded] is false when the trial ended during the countdown or
     * the wait for the glasses, before any data was kept.
     */
    suspend fun endTrial(trial: Trial, outcome: TrialOutcome, reason: String?, recorded: Boolean)
}

/** The wearer-facing side of a trial: sounds and speech. */
interface TrialFeedback {
    fun countdown(trial: Trial, secondsLeft: Int) {}
    fun waitingForGlasses(trial: Trial) {}
    fun recordingStarted(trial: Trial) {}
    fun cue(trial: Trial, label: String, index: Int, style: CueStyle) {}
    fun finished(trial: Trial, outcome: TrialOutcome, reason: String?) {}
}

/**
 * Drives one trial through the same phases as the web IMU Lab's runner
 * (frontend/visor-webapp/protocol.js): a preparation countdown during which
 * nothing is recorded, then a fixed-length recording with machine-readable cue
 * marks — the ground truth the analysis segments against.
 *
 * One addition the native path needs: between the countdown and the recording
 * sits a wait for the glasses. A DAT session, its camera and its motion
 * capability take seconds to come up, so the host starts them when the trial
 * starts and the countdown runs meanwhile; recording begins only once data is
 * actually flowing.
 *
 * Cues are scheduled against the recording's start time rather than chained
 * delay after delay, so one late cue never shifts every cue after it.
 *
 * Exactly one [TrialHost.endTrial] per trial, however it ends: completion,
 * [abort], [fail], or a stream that never came up — whichever gets there first.
 *
 * @param now monotonic milliseconds — `SystemClock.elapsedRealtime` on device,
 *   the virtual clock in tests.
 */
class TrialRunner(
    private val scope: CoroutineScope,
    private val host: TrialHost,
    private val feedback: TrialFeedback,
    private val now: () -> Long,
) {
    private val _state = MutableStateFlow<RunnerState>(RunnerState.Idle)
    val state: StateFlow<RunnerState> = _state.asStateFlow()

    private class Run(val trial: Trial) {
        lateinit var job: Job
        @Volatile var recording = false
        val finished = AtomicBoolean(false)
    }

    @Volatile private var current: Run? = null

    val isRunning: Boolean get() = current?.finished?.get() == false

    fun start(trial: Trial) {
        check(!isRunning) { "A trial is already running" }
        val run = Run(trial)
        current = run
        run.job = scope.launch(start = CoroutineStart.LAZY) { execute(run) }
        run.job.start()
    }

    /** The wearer or tester stopped the trial. Anything recorded is kept, marked aborted. */
    fun abort(reason: String? = null) = stop(TrialOutcome.ABORTED, reason)

    /** The glasses gave out mid-trial. Anything recorded is kept, marked failed. */
    fun fail(reason: String) = stop(TrialOutcome.FAILED, reason)

    private fun stop(outcome: TrialOutcome, reason: String?) {
        val run = current ?: return
        if (run.finished.get()) return
        scope.launch {
            run.job.cancel()
            run.job.join()
            finish(run, outcome, reason)
        }
    }

    private suspend fun execute(run: Run) {
        val trial = run.trial
        for (secondsLeft in trial.prepSec downTo 1) {
            _state.value = RunnerState.Preparing(trial, secondsLeft)
            feedback.countdown(trial, secondsLeft)
            delay(1_000)
        }

        _state.value = RunnerState.WaitingForGlasses(trial)
        feedback.waitingForGlasses(trial)
        val problem = host.awaitStreams(trial)
        if (problem != null) {
            finish(run, TrialOutcome.FAILED, problem)
            return
        }

        host.beginRecording(trial)
        run.recording = true
        host.mark("trial_start", mapOf("trialId" to trial.id))
        feedback.recordingStarted(trial)

        val startedAt = now()
        val durationMs = trial.durationSec * 1_000L
        val cue = trial.cue
        var nextCue = 0
        var lastLabel: String? = null

        while (true) {
            val elapsed = now() - startedAt
            if (elapsed >= durationMs) break
            if (cue != null && elapsed >= nextCue * cue.intervalMs) {
                val label = cue.steps[nextCue % cue.steps.size]
                host.mark("cue", mapOf("index" to nextCue, "label" to label))
                feedback.cue(trial, label, nextCue, cue.style)
                lastLabel = label
                nextCue++
            }
            _state.value = RunnerState.Recording(trial, elapsed, lastLabel, nextCue - 1)
            val nextCueAt = if (cue != null) nextCue * cue.intervalMs else Long.MAX_VALUE
            val wakeAt = minOf(durationMs, nextCueAt, elapsed + TICK_MS)
            delay((wakeAt - elapsed).coerceAtLeast(1))
        }
        finish(run, TrialOutcome.COMPLETED, null)
    }

    private suspend fun finish(run: Run, outcome: TrialOutcome, reason: String?) {
        if (!run.finished.compareAndSet(false, true)) return
        withContext(NonCancellable) {
            val trial = run.trial
            if (run.recording) {
                host.mark(
                    "trial_end",
                    mapOf("outcome" to outcome.name.lowercase(), "reason" to reason),
                )
            }
            host.endTrial(trial, outcome, reason, run.recording)
            _state.value = RunnerState.Finished(trial, outcome, reason, run.recording)
            feedback.finished(trial, outcome, reason)
        }
    }

    private companion object {
        /** How often the elapsed time on screen advances. */
        const val TICK_MS = 250L
    }
}
