package ucf.visor.motion

import android.annotation.SuppressLint
import android.util.Log
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.types.DeviceSessionError
import com.meta.wearable.dat.motion.Motion
import com.meta.wearable.dat.motion.addMotion
import com.meta.wearable.dat.motion.removeMotion
import com.meta.wearable.dat.motion.types.MotionConfiguration
import com.meta.wearable.dat.motion.types.MotionSamplingRate
import com.meta.wearable.dat.motion.types.MotionSource
import com.meta.wearable.dat.motion.types.MotionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ucf.visor.wearables.describeSessionError

/**
 * The glasses' head movement, live: MWDAT 1.0's Motion capability, attached to
 * the glasses session VISOR already holds for the camera
 * (wearables/HeldGlassesSession), turned into where the head points
 * ([HeadingTracker]) and how fast it is moving.
 *
 * What the head-motion lab learned and this keeps (motionlab/LabGlassesLink):
 *
 * - **Attach once the camera is streaming.** A capability added while the
 *   device is still arriving fails with DEVICE_DISCONNECTED. Look around
 *   starts the glasses camera first and attaches this after.
 * - **Subscribe, then start.** `samples` does not replay, and `addMotion`
 *   leaves Motion STOPPED until `start()`.
 * - **A stop nobody asked for is revived.** Something else on the link can put
 *   Motion down; it is started again, a few times, before giving up.
 *
 * It asks for 30 Hz, not the lab's 60: a head turning to look around moves
 * well under 1 Hz, and the camera stream sharing the Bluetooth link with it
 * gets the rest. Only the glasses' own samples count, never a Neural Band's.
 *
 * Every SDK call that can block on a failing link runs off the main thread.
 */
class HeadMotion {

    enum class Status { OFF, STARTING, RUNNING, FAILED }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val tracker = HeadingTracker()

    private val _pose = MutableStateFlow<HeadingTracker.Pose?>(null)

    /** The latest head pose, or null while no samples are arriving. */
    val pose: StateFlow<HeadingTracker.Pose?> = _pose.asStateFlow()

    private val _status = MutableStateFlow(Status.OFF)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Why Motion failed, in words fit to speak, while [status] is FAILED. */
    @Volatile
    var failure: String? = null
        private set

    private var session: DeviceSession? = null
    private var motion: Motion? = null
    private var jobs: List<Job> = emptyList()

    @Volatile
    private var stopping = false

    /** Whether Motion is attached to [target] already. */
    fun isAttachedTo(target: DeviceSession): Boolean = session === target && motion != null

    /**
     * Attaches Motion to [target], a started session, and starts it. Returns
     * null once it is starting (samples follow; see [pose]), or why it could
     * not, in words fit to speak.
     */
    @SuppressLint("AutoCloseableUse") // Motion lives until detach().
    fun attach(target: DeviceSession): String? {
        if (isAttachedTo(target)) return null
        detach()
        stopping = false
        failure = null
        _status.value = Status.STARTING
        var problem: String? = null
        target.addMotion(MotionConfiguration(samplingRate = MotionSamplingRate.HZ_30))
            .onSuccess { attached ->
                session = target
                motion = attached
                synchronized(tracker) { tracker.recenter() }
                jobs = listOf(
                    // UNDISPATCHED: samples don't replay, so the subscription
                    // must exist before start() below.
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        attached.samples.collect { sample ->
                            if (sample.source != MotionSource.GLASSES) return@collect
                            val gyro = sample.gyroscope ?: return@collect
                            val pose = synchronized(tracker) {
                                tracker.update(
                                    tNanos = sample.timestampNs,
                                    gyro = floatArrayOf(gyro.x, gyro.y, gyro.z),
                                    accel = sample.accelerometer?.let { floatArrayOf(it.x, it.y, it.z) },
                                )
                            }
                            _pose.value = pose
                            if (_status.value == Status.STARTING) _status.value = Status.RUNNING
                        }
                    },
                    scope.launch {
                        attached.state.collect { state ->
                            Log.i(TAG, "Motion $state")
                            if (state == MotionState.STOPPED && !stopping) revive(attached)
                        }
                    },
                    scope.launch {
                        attached.errors.collect { error ->
                            if (error != null) Log.w(TAG, "Motion error ${error.name}: ${error.description}")
                        }
                    },
                )
                attached.start()
            }
            .onFailure { error, _ ->
                problem = if (error == DeviceSessionError.CAPABILITY_DENIED) {
                    MOTION_NOT_ENABLED
                } else {
                    "I couldn't use your glasses' motion sensors. ${describeSessionError(error)}"
                }
            }
        problem?.let(::fail)
        return problem
    }

    /** Makes the head's current direction "ahead" (yaw 0). */
    fun recenter() {
        synchronized(tracker) { tracker.recenter() }
        _pose.value = _pose.value?.copy(yawDeg = 0.0)
    }

    /** Stops Motion and takes it off the session. Safe to call more than once. */
    fun detach() {
        stopping = true
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        val stoppingMotion = motion
        val fromSession = session
        motion = null
        session = null
        _pose.value = null
        if (_status.value != Status.FAILED) _status.value = Status.OFF
        if (stoppingMotion == null) return
        // Removal is terminal and can block on a failing link: off the main
        // thread, and not waited for longer than a moment.
        scope.launch(Dispatchers.IO) {
            withTimeoutOrNull(STOP_TIMEOUT_MS) {
                runCatching { stoppingMotion.stop() }.onFailure { Log.w(TAG, "motion.stop failed", it) }
                runCatching { fromSession?.removeMotion() }.onFailure { Log.w(TAG, "removeMotion failed", it) }
            } ?: Log.w(TAG, "Motion did not stop within $STOP_TIMEOUT_MS ms; carrying on")
        }
    }

    private var revival: Job? = null

    /**
     * Motion stopped without being asked to (or has not come up yet: STOPPED
     * is also the state it is born in). Starts it again a few times before
     * saying it failed. `start()` only acts on a stopped capability.
     */
    private fun revive(target: Motion) {
        if (revival?.isActive == true) return
        revival = scope.launch {
            repeat(REVIVAL_ATTEMPTS) {
                delay(REVIVAL_DELAY_MS)
                if (stopping || motion !== target) return@launch
                if (target.state.value != MotionState.STOPPED) return@launch
                Log.i(TAG, "Starting Motion again")
                target.start()
            }
            if (!stopping && motion === target && target.state.value == MotionState.STOPPED) {
                fail(MOTION_STOPPED)
            }
        }
    }

    private fun fail(spoken: String) {
        Log.w(TAG, spoken)
        failure = spoken
        _status.value = Status.FAILED
    }

    companion object {
        private const val TAG = "VisorHeadMotion"
        private const val STOP_TIMEOUT_MS = 3_000L
        private const val REVIVAL_ATTEMPTS = 3
        private const val REVIVAL_DELAY_MS = 1_000L

        const val MOTION_NOT_ENABLED =
            "Your glasses' motion sensors aren't turned on for VISOR. " +
                "Enable Motion for VISOR in the Wearables Developer Center."
        const val MOTION_STOPPED = "Your glasses' motion sensors stopped."
    }
}
