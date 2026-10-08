package ucf.visor.wearables

import android.util.Log
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.voiceinvocations.VoiceInvocationsStream
import com.meta.wearable.dat.core.voiceinvocations.startVoiceInvocationsStream
import com.meta.wearable.dat.core.voiceinvocations.types.SessionState
import com.meta.wearable.dat.core.voiceinvocations.types.VoiceInvocationError
import com.meta.wearable.dat.core.voiceinvocations.types.actions.LaunchApp
import com.meta.wearable.dat.core.voiceinvocations.types.actions.ResponseHandle
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "Hey Meta, start VISOR" — MWDAT 1.0 voice invocations, for a wearer who
 * should not have to find, unlock and read their phone to open VISOR.
 *
 * Modelled on Meta's VoiceInvocationsSample: the stream is **process-scoped**,
 * not tied to a screen, so a voice command that cold-launches VISOR is still
 * delivered when the UI is finally ready to act on it. Invocations wait in
 * [pending] (a StateFlow, so a late subscriber sees them) until VISOR has acted
 * and acknowledged each one exactly once — the glasses wait for that answer.
 *
 * Needs the app registered in the Wearables Developer Center with the Voice
 * Invocation permission approved. Meta documents two traps: voice routing does
 * not work with Developer Mode on, and an app name containing "AI" is
 * intercepted by the assistant.
 */
class VoiceLaunches private constructor(
    private val streamFactory: () -> VoiceInvocationsStream,
) {
    /** A voice launch VISOR has not answered yet. */
    data class PendingLaunch(val id: Long, val receivedAtMs: Long, internal val handle: ResponseHandle)

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught exception in the voice invocation stream", throwable)
    }
    private val nextId = AtomicLong(0)

    private val _pending = MutableStateFlow<List<PendingLaunch>>(emptyList())
    val pending: StateFlow<List<PendingLaunch>> = _pending.asStateFlow()

    private val _state = MutableStateFlow(SessionState.STOPPED)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<VoiceInvocationError>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val errors: SharedFlow<VoiceInvocationError> = _errors.asSharedFlow()

    private var stream: VoiceInvocationsStream? = null
    private var streamScope: CoroutineScope? = null

    /** Opens the stream. Idempotent; requires `Wearables.initialize` to have run. */
    @Synchronized
    fun start() {
        if (stream != null) return
        val newStream = runCatching { streamFactory() }
            .onFailure { Log.w(TAG, "Voice invocation stream unavailable", it) }
            .getOrNull() ?: return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)
        stream = newStream
        streamScope = scope
        scope.launch { newStream.state.collect { _state.value = it } }
        scope.launch {
            newStream.invocations.collect { invocation ->
                when (invocation) {
                    is LaunchApp -> {
                        val launch = PendingLaunch(
                            id = nextId.incrementAndGet(),
                            receivedAtMs = System.currentTimeMillis(),
                            handle = invocation.responseHandle,
                        )
                        Log.i(TAG, "Voice launch #${launch.id} received")
                        _pending.update { (it + launch).takeLast(MAX_PENDING) }
                    }
                }
            }
        }
        scope.launch {
            newStream.errors.collect { error ->
                Log.w(TAG, "Voice invocation error: ${error.name} — ${error.description}")
                _errors.emit(error)
            }
        }
    }

    @Synchronized
    fun stop() {
        stream?.close()
        stream = null
        streamScope?.cancel()
        streamScope = null
        _state.value = SessionState.STOPPED
    }

    /**
     * Answers one launch and removes it from [pending]. Exactly one answer per
     * launch, even on a double call: only the caller that removes it sends.
     *
     * @param message optional text returned to the glasses as the action output.
     * @return whether the answer reached the glasses.
     */
    suspend fun acknowledge(launch: PendingLaunch, success: Boolean, message: String? = null): Boolean {
        var removed = false
        _pending.update { current ->
            if (current.any { it.id == launch.id }) {
                removed = true
                current.filterNot { it.id == launch.id }
            } else {
                removed = false
                current
            }
        }
        if (!removed) return false
        val delivered = runCatching {
            if (success) launch.handle.sendSuccess(message) else launch.handle.sendFailure(message)
        }.getOrElse {
            Log.w(TAG, "Could not answer voice launch #${launch.id}", it)
            false
        }
        Log.i(TAG, "Voice launch #${launch.id} answered success=$success delivered=$delivered")
        return delivered
    }

    companion object {
        private const val TAG = "VisorVoiceLaunch"
        private const val MAX_PENDING = 8

        @Volatile private var instance: VoiceLaunches? = null

        fun get(): VoiceLaunches = instance ?: synchronized(this) {
            instance ?: VoiceLaunches { Wearables.startVoiceInvocationsStream(AutoDeviceSelector()) }
                .also { instance = it }
        }
    }
}
