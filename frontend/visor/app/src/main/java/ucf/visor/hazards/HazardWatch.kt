package ucf.visor.hazards

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ucf.visor.network.Hazard

/** Whether the hazard watch is running, and whether it can see. */
enum class HazardWatchState { OFF, WATCHING, CANT_SEE }

/** One checked frame: what the server found in a picture [width] x [height] taken at [timeMs]. */
data class CheckedFrame(val hazards: List<Hazard>, val width: Int, val height: Int, val timeMs: Long)

/**
 * The hazard watch: while it is on, it checks frame after frame, and says
 * only what [AlertPolicy] decides is worth saying. One check at a time, always
 * the newest frame, so nothing is said late; at most one every
 * [HazardWatchConfig.checkIntervalMs].
 *
 * Silence must never mean broken: after [HazardWatchConfig.cantSeeMs] with no
 * frame checked, it says so ([onCantSee]), reminds the user while that lasts,
 * and says when it can see again ([onBack]).
 *
 * Holds no Android types, so HazardWatchLoopTest drives it with scripted
 * frames: [check] takes a picture and asks the server (null when either
 * failed), and [now] is the clock frames are timed by. [config] is read at
 * each start, so a changed profile applies to the next run.
 */
class HazardWatch(
    private val scope: CoroutineScope,
    private val check: suspend () -> CheckedFrame?,
    private val onAlert: (Alert) -> Unit,
    private val onCantSee: () -> Unit,
    private val onBack: () -> Unit,
    private val now: () -> Long,
    private val config: () -> HazardWatchConfig = { HazardWatchConfig() },
) {
    private var job: Job? = null

    private val _state = MutableStateFlow(HazardWatchState.OFF)
    val state: StateFlow<HazardWatchState> = _state.asStateFlow()

    val isOn: Boolean get() = job?.isActive == true

    /** Starts watching. Does nothing if it already is. */
    fun start() {
        if (isOn) return
        _state.value = HazardWatchState.WATCHING
        // A new tracker and policy each run: nothing said before carries over.
        val settings = config()
        job = scope.launch { watch(settings, HazardTracker(settings), AlertPolicy(settings)) }
    }

    /** Stops watching: the check in flight is cancelled and what it found is never said. */
    fun stop() {
        job?.cancel()
        job = null
        _state.value = HazardWatchState.OFF
    }

    private suspend fun CoroutineScope.watch(config: HazardWatchConfig, tracker: HazardTracker, policy: AlertPolicy) {
        val startedAt = now()
        var lastCheckedMs: Long? = null
        var lastToldMs = 0L
        // The check in flight. A check that hangs (a stalled upload, a slow
        // camera) is waited on again, never doubled up: piling new pictures
        // behind a stuck one would only make every answer later.
        var pending: Deferred<CheckedFrame?>? = null

        while (isActive) {
            val loopStartMs = now()
            val inFlight = pending?.takeIf { it.isActive } ?: async { check() }.also { pending = it }
            // Waiting is capped, so "can't see" is never late, however long the check takes.
            val frame = withTimeoutOrNull(config.cantSeeMs) { inFlight.await() }
            // Stopped while that check was in flight: what it found is never said.
            ensureActive()

            if (frame != null) {
                lastCheckedMs = now()
                if (_state.value == HazardWatchState.CANT_SEE) onBack()
                _state.value = HazardWatchState.WATCHING
                val tracks = tracker.update(frame.hazards, frame.width, frame.height, frame.timeMs)
                policy.next(tracks, nowMs = now(), frameTimeMs = frame.timeMs)?.let(onAlert)
            } else {
                // Starting up (opening the glasses' camera) gets longer than a
                // dropout does, before it counts as not seeing.
                val blindForMs = now() - (lastCheckedMs ?: (startedAt - config.cantSeeMs + config.startGraceMs))
                val firstTime = _state.value != HazardWatchState.CANT_SEE && blindForMs >= config.cantSeeMs
                val reminder = _state.value == HazardWatchState.CANT_SEE &&
                    now() - lastToldMs >= config.cantSeeReminderMs
                if (firstTime || reminder) {
                    _state.value = HazardWatchState.CANT_SEE
                    lastToldMs = now()
                    onCantSee()
                }
            }
            delay((config.checkIntervalMs - (now() - loopStartMs)).coerceAtLeast(0))
        }
    }
}
