package ucf.visor.ui.glasses

import android.util.Log
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.DeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.display.Display
import com.meta.wearable.dat.display.addDisplay
import com.meta.wearable.dat.display.removeDisplay
import com.meta.wearable.dat.display.views.Direction
import com.meta.wearable.dat.display.views.TextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import ucf.visor.ui.voice.VoiceCommand

/**
 * On-glasses tap-fallback navigation, rendered through the mwdat-display module.
 *
 * The Ray-Ban Display's captouch temple strip and Neural Band gestures aren't
 * exposed to apps as raw events — MWDAT keeps that internal to its own UI
 * component model (see documentation/research/meta-mrbd-capabilities.md and
 * the MWDAT display-overview docs it links). The only supported way to react
 * to a physical tap is to render real buttons on the glasses' own screen via
 * [Display.sendContent] and let the glasses OS's own focus/select gestures
 * drive each button's `onClick`.
 *
 * Mirrors [ucf.visor.ui.voice.VoiceNavigationController]'s shape (an
 * enable/disable switch, a [commands] flow) but owns its own device-session
 * capability rather than the microphone, and emits the exact same
 * [VoiceCommand] type — so VisorLayout's single dispatcher already knows how
 * to handle a glasses tap, with nothing glasses-specific to add there.
 *
 * Connects reactively to [deviceSelector]'s active-device flow rather than
 * eagerly: on an emulator or phone with no glasses paired this never attempts
 * a session at all, so there's nothing to fail or log.
 */
class GlassesNavigationController(
    private val deviceSelector: DeviceSelector,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var session: DeviceSession? = null
    private var display: Display? = null
    private var deviceAvailabilityJob: Job? = null
    private var sessionStateJob: Job? = null
    private var sessionErrorJob: Job? = null

    private var pendingScreen: GlassesNavScreen? = null
    private var lastSentScreen: GlassesNavScreen? = null

    private val _commands = MutableSharedFlow<VoiceCommand>(extraBufferCapacity = 1)
    val commands: SharedFlow<VoiceCommand> = _commands.asSharedFlow()

    @Volatile
    private var enabled = false

    /** Turns the on-glasses nav UI on/off — see UserProfile.glassesTapNavigationEnabled. */
    fun setEnabled(isEnabled: Boolean) {
        if (enabled == isEnabled) return
        enabled = isEnabled
        if (isEnabled) startWatchingForDevice() else teardown()
    }

    /**
     * Renders [screen]'s buttons on the glasses, replacing whatever was shown
     * before — sendContent has no partial-update API, so every navigation
     * change re-sends the full button tree. Safe to call before a display is
     * attached (or while disabled): the most recent [screen] is cached and
     * sent as soon as the display becomes available.
     */
    fun showScreen(screen: GlassesNavScreen) {
        pendingScreen = screen
        sendPendingScreenIfReady()
    }

    private fun startWatchingForDevice() {
        deviceAvailabilityJob = scope.launch {
            deviceSelector.activeDeviceFlow().collect { device ->
                if (device != null) {
                    if (session == null) createSession()
                } else {
                    tearDownSession()
                }
            }
        }
    }

    private fun createSession() {
        Wearables.createSession(deviceSelector)
            .onSuccess { created ->
                session = created
                sessionErrorJob = scope.launch {
                    created.errors.collect { error ->
                        Log.w(TAG, "Glasses display session error: ${error.description}")
                    }
                }
                sessionStateJob = scope.launch {
                    created.state.collect { state ->
                        if (state == DeviceSessionState.STARTED && display == null) {
                            attachDisplay(created)
                        }
                    }
                }
                created.start()
            }
            .onFailure { error, _ ->
                Log.w(TAG, "Unable to start glasses display session: ${error.description}")
            }
    }

    private fun attachDisplay(session: DeviceSession) {
        session.addDisplay()
            .onSuccess { attached ->
                display = attached
                sendPendingScreenIfReady()
            }
            .onFailure { error, _ ->
                Log.w(TAG, "Unable to attach glasses display: ${error.description}")
            }
    }

    private fun sendPendingScreenIfReady() {
        if (!enabled) return
        val screen = pendingScreen ?: return
        val activeDisplay = display ?: return
        if (screen == lastSentScreen) return
        lastSentScreen = screen
        scope.launch {
            activeDisplay
                .sendContent {
                    flexBox(direction = Direction.COLUMN, gap = 16, padding = 24) {
                        text(screen.title, style = TextStyle.HEADING)
                        buttonGroup {
                            screen.items.forEach { item ->
                                button(
                                    item.label,
                                    style = item.style,
                                    iconName = item.icon,
                                    onClick = { _commands.tryEmit(item.command) },
                                )
                            }
                        }
                    }
                }
                .onFailure { error, _ ->
                    Log.w(TAG, "Failed to render glasses nav screen: ${error.description}")
                    // Let the next state-driven showScreen() call retry the send.
                    lastSentScreen = null
                }
        }
    }

    private fun tearDownSession() {
        sessionStateJob?.cancel()
        sessionStateJob = null
        sessionErrorJob?.cancel()
        sessionErrorJob = null
        session?.removeDisplay()
        display = null
        session?.stop()
        session = null
        lastSentScreen = null
    }

    private fun teardown() {
        deviceAvailabilityJob?.cancel()
        deviceAvailabilityJob = null
        tearDownSession()
    }

    fun shutdown() {
        teardown()
        scope.cancel()
    }

    private companion object {
        const val TAG = "GlassesNavigationController"
    }
}
