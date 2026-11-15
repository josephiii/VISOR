package ucf.visor.wearables

import android.util.Log
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.SpecificDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.DeviceSessionError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One glasses session for the camera, held while VISOR is in use, with every
 * picture taken inside it.
 *
 * Why not one session per picture, as VISOR used to: each session that ends
 * hands the glasses' Bluetooth data channel back to the Meta AI app, which
 * often reopens it at that very moment, and the SDK's own link to the glasses
 * is cut. Meta's staff: "If the link drops with no session open, the SDK does
 * not bring it back in the same process", while a held session reconnects by
 * itself (meta-wearables-dat-android issues #138, #192, #215). Field logs from
 * 2026-11-13 show exactly that: the first picture worked, and the session
 * ending after it left VISOR unable to see the glasses until it restarted.
 *
 * [setKeep] says whether to hold the session between pictures (VisorEngine:
 * while VISOR is on screen or listening in the background). With it off, a
 * picture still opens a session, and it is closed after a grace period
 * ([LINGER_MS]) rather than at once, so a run of commands shares it. Closing
 * is the risky moment, so it happens as rarely as VISOR's use allows.
 *
 * The lease ([GlassesLease]) is held *yielding*: the head-motion lab can still
 * take the glasses, and the session is closed for it.
 *
 * Every SDK call that can block on a failing link runs on [Dispatchers.IO]:
 * a hung `stop()` on the main thread froze VISOR (ANR, 2026-11-13).
 */
class HeldGlassesSession(private val scope: CoroutineScope) {

    /** Why a session could not be had, said to the wearer, and whether the SDK has lost the glasses. */
    data class Failure(val error: DeviceSessionError?, val paused: Boolean = false)

    private val lock = Mutex()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var session: DeviceSession? = null
    private var watchJobs: List<Job> = emptyList()
    private var lingerJob: Job? = null

    @Volatile
    private var keep = false

    /**
     * How long a session opened for a picture stays open when it is not held
     * ([setKeep] off). Zero on display glasses with tap navigation, which
     * needs its own session back at once.
     */
    @Volatile
    var lingerMs: Long = LINGER_MS

    /** The last error the held session reported, or why opening it failed. */
    @Volatile
    var lastError: DeviceSessionError? = null
        private set

    private val _state = MutableStateFlow<DeviceSessionState?>(null)

    /** The held session's state, or null with none open. */
    val state: StateFlow<DeviceSessionState?> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)

    /** A picture is being taken in the session right now. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /**
     * Hold the session between pictures, or not. Turning it off closes the
     * session after [LINGER_MS] unless a picture is taken or it is turned
     * back on before then.
     */
    fun setKeep(hold: Boolean) {
        if (keep == hold) return
        keep = hold
        if (hold) lingerJob?.cancel() else scheduleClose()
    }

    /** Features holding the session open whatever [setKeep] says (see [pin]). */
    private val pins = mutableSetOf<String>()

    /**
     * Keeps the session open for [owner] until [unpin], even where VISOR would
     * otherwise close it after each picture (display glasses with tap
     * navigation): Look around streams the glasses' motion in it between its
     * pictures, and the room's directions are only good while that stream
     * lasts. A session the glasses end is still ended; the pin does not reopen it.
     */
    @Synchronized
    fun pin(owner: String) {
        pins += owner
        lingerJob?.cancel()
    }

    @Synchronized
    fun unpin(owner: String) {
        if (pins.remove(owner) && !keep && pins.isEmpty()) scheduleClose()
    }

    @Synchronized
    private fun isPinned(): Boolean = pins.isNotEmpty()

    /**
     * Runs [block] in a started session, opening one if needed. Returns the
     * block's result, or the [Failure] that kept it from running.
     */
    suspend fun <T> withSession(block: suspend (DeviceSession) -> T): Result<T> = lock.withLock {
        lingerJob?.cancel()
        val opened = openedSession() ?: return@withLock Result.Unavailable(Failure(lastError))
        // The wearer can pause a session (a tap on the glasses, taking them off).
        if (!awaitStarted(opened, PAUSE_WAIT_MS)) {
            return@withLock Result.Unavailable(Failure(lastError, paused = _state.value == DeviceSessionState.PAUSED))
        }
        _busy.value = true
        try {
            Result.Done(block(opened))
        } finally {
            _busy.value = false
            if (!keep && !isPinned()) scheduleClose()
        }
    }

    sealed class Result<out T> {
        data class Done<T>(val value: T) : Result<T>()
        data class Unavailable(val failure: Failure) : Result<Nothing>()
    }

    /** Closes the session now, without waiting for it to stop. */
    fun close() {
        lingerJob?.cancel()
        val closing = session ?: return
        session = null
        watchJobs.forEach { it.cancel() }
        watchJobs = emptyList()
        _state.value = null
        GlassesLease.release(LEASE_OWNER)
        Log.i(TAG, "Closing the held glasses session")
        ioScope.launch { runCatching { closing.stop() }.onFailure { Log.w(TAG, "session.stop failed", it) } }
    }

    /** The held session if it is alive, or a newly opened one, or null. */
    private suspend fun openedSession(): DeviceSession? {
        session?.let { current ->
            if (_state.value != DeviceSessionState.STOPPED && _state.value != DeviceSessionState.STOPPING) {
                return current
            }
            close()
        }
        return open()
    }

    private suspend fun open(): DeviceSession? {
        val deviceId = preferredDevice()?.first ?: run {
            lastError = DeviceSessionError.NO_ELIGIBLE_DEVICE
            return null
        }
        if (!GlassesLease.acquireYielding(LEASE_OWNER) { scope.launch { close() } }) {
            lastError = DeviceSessionError.SESSION_ALREADY_EXISTS
            return null
        }
        val created = createWithRetry(deviceId) ?: run {
            GlassesLease.release(LEASE_OWNER)
            return null
        }
        lastError = null
        session = created
        _state.value = DeviceSessionState.IDLE
        watchJobs = listOf(
            scope.launch {
                created.state.collect { state ->
                    _state.value = state
                    Log.i(TAG, "Held session: $state")
                    if (state == DeviceSessionState.STARTED) GlassesAppUpdate.onSessionStarted()
                    // Ended by the glasses (tap and hold, a dropped link): let it
                    // go, so the next picture opens a fresh one.
                    if (state == DeviceSessionState.STOPPED && session === created) {
                        session = null
                        GlassesLease.release(LEASE_OWNER)
                        // No session now: null, not STOPPED, so VisorEngine opens a fresh one.
                        _state.value = null
                        watchJobs.forEach { if (it != coroutineContext[Job]) it.cancel() }
                    }
                }
            },
            scope.launch {
                created.errors.collect { error ->
                    Log.w(TAG, "Held session error ${error.name}: ${error.description}")
                    if (!error.isWarningOnly) lastError = error
                    GlassesAppUpdate.report(error)
                }
            },
        )
        withContext(Dispatchers.IO) { created.start() }
        // Asked to give way (the lab took the glasses) while this was opening:
        // close() found nothing to close then, so do it now.
        if (GlassesLease.holder.value != LEASE_OWNER) {
            Log.i(TAG, "Gave the glasses away while opening; closing again")
            close()
            lastError = DeviceSessionError.SESSION_ALREADY_EXISTS
            return null
        }
        return created
    }

    private suspend fun awaitStarted(opened: DeviceSession, pauseWaitMs: Long): Boolean {
        if (_state.value == DeviceSessionState.STARTED) return true
        val wait = if (_state.value == DeviceSessionState.PAUSED) pauseWaitMs else START_TIMEOUT_MS
        val started = withTimeoutOrNull(wait) {
            opened.state.first { it == DeviceSessionState.STARTED || it == DeviceSessionState.STOPPED }
        }
        return started == DeviceSessionState.STARTED
    }

    private suspend fun createWithRetry(deviceId: com.meta.wearable.dat.core.types.DeviceIdentifier): DeviceSession? {
        // Another feature's session (tap navigation, the lab) may still be
        // stopping: SESSION_ALREADY_EXISTS in that window is worth waiting out.
        repeat(SESSION_ATTEMPTS) { attempt ->
            val result = withContext(Dispatchers.IO) { Wearables.createSession(SpecificDeviceSelector(deviceId)) }
            result.getOrNull()?.let { return it }
            val error = result.errorOrNull()
            lastError = error
            Log.w(TAG, "createSession attempt ${attempt + 1} failed: ${error?.name}")
            error?.let(GlassesAppUpdate::report)
            if (error != DeviceSessionError.SESSION_ALREADY_EXISTS) return null
            delay(SESSION_RETRY_MS)
        }
        return null
    }

    private fun scheduleClose() {
        lingerJob?.cancel()
        if (session == null) return
        lingerJob = scope.launch {
            delay(lingerMs)
            withContext(NonCancellable) { if (!keep && !isPinned() && !_busy.value) close() }
        }
    }

    companion object {
        private const val TAG = "VisorHeldSession"
        const val LEASE_OWNER = "glasses_camera"

        /** How long a session opened for a picture stays open when VISOR is not holding one. */
        const val LINGER_MS = 2 * 60_000L

        const val START_TIMEOUT_MS = 15_000L

        /** How long a picture waits for a paused session (glasses tapped or taken off) to resume. */
        const val PAUSE_WAIT_MS = 4_000L

        private const val SESSION_ATTEMPTS = 20
        private const val SESSION_RETRY_MS = 250L
    }
}
