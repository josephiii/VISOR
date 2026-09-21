package ucf.visor.ui.glasses

import android.util.Log
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.DeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.LinkState
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.display.Display
import com.meta.wearable.dat.display.addDisplay
import com.meta.wearable.dat.display.removeDisplay
import com.meta.wearable.dat.display.types.DisplayState
import com.meta.wearable.dat.display.views.Direction
import com.meta.wearable.dat.display.views.TextColor
import com.meta.wearable.dat.display.views.TextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
 *
 * [deviceSelector] is a provider, not a resolved instance, and nothing here
 * touches it until [onWearablesReady]: constructing a DeviceSelector reaches
 * into the SDK singleton, which throws WearablesException until
 * `Wearables.initialize()` has run (MainActivity does that from its runtime
 * permission callback, well after onCreate).
 */
class GlassesNavigationController(
    private val deviceSelector: () -> DeviceSelector,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var session: DeviceSession? = null
    private var display: Display? = null
    private var deviceAvailabilityJob: Job? = null
    private var sessionStateJob: Job? = null
    private var sessionErrorJob: Job? = null
    private var displayStateJob: Job? = null
    private var displayStarted = false

    private var pendingScreen: GlassesNavScreen? = null
    private var lastSentScreen: GlassesNavScreen? = null

    private val _commands = MutableSharedFlow<VoiceCommand>(extraBufferCapacity = 1)
    val commands: SharedFlow<VoiceCommand> = _commands.asSharedFlow()

    @Volatile
    private var enabled = false
    private var wearablesReady = false

    /** Turns the on-glasses nav UI on/off — see UserProfile.glassesTapNavigationEnabled. */
    fun setEnabled(isEnabled: Boolean) {
        if (enabled == isEnabled) return
        enabled = isEnabled
        updateWatching()
    }

    /**
     * Call once `Wearables.initialize()` has run and its permissions were
     * granted — every SDK entry point below, starting with resolving the
     * DeviceSelector itself, throws until then.
     */
    fun onWearablesReady() {
        if (wearablesReady) return
        wearablesReady = true
        updateWatching()
    }

    private fun updateWatching() {
        if (enabled && wearablesReady) {
            if (deviceAvailabilityJob == null) startWatchingForDevice()
        } else {
            teardown()
        }
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

    /**
     * A session is only viable once the glasses' own transport is up
     * ([LinkState.CONNECTED]) *and* the app has completed MWDAT's on-device
     * registration handshake (the "Register" button on HardwarePairingScreen ->
     * `Wearables.startRegistration`).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startWatchingForDevice() {
        deviceAvailabilityJob = scope.launch {
            combine(
                deviceSelector().activeDeviceFlow().flatMapLatest { deviceId ->
                    deviceId?.let { id -> Wearables.devicesMetadata[id]?.map { it.linkState } }
                        ?: flowOf<LinkState?>(null)
                },
                Wearables.registrationState,
            ) { linkState, registration -> linkState to registration }
                .distinctUntilChanged()
                .collect { (linkState, registration) ->
                    val ready = linkState == LinkState.CONNECTED &&
                            registration == RegistrationState.REGISTERED
                    Log.i(
                        TAG,
                        "Glasses nav gate: link=$linkState registration=$registration ready=$ready"
                    )
                    if (ready) {
                        if (session == null) createSession()
                    } else {
                        tearDownSession()
                    }
                }
        }
    }

    private fun createSession() {
        Wearables.createSession(deviceSelector())
            .onSuccess { created ->
                session = created
                sessionErrorJob = scope.launch {
                    created.errors.collect { error ->
                        Log.w(
                            TAG,
                            "Glasses display session error: ${error.name} (${error.description})"
                        )
                    }
                }
                sessionStateJob = scope.launch {
                    var hasStarted = false
                    created.state.collect { state ->
                        Log.i(TAG, "Glasses session state: $state")
                        if (state == DeviceSessionState.STARTED) {
                            hasStarted = true
                            if (display == null) attachDisplay(created)
                        }
                        // A session the device tore down never restarts itself, so
                        // release it rather than leaving a dead handle that makes
                        // startWatchingForDevice skip every future createSession().
                        if (state == DeviceSessionState.STOPPED && hasStarted) {
                            tearDownSession()
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
                Log.i(TAG, "Glasses display attached; waiting for it to start")
                // Content sent before the display reports STARTED is dropped —
                // attaching only allocates the capability, the glasses still
                // have to bring the surface up.
                displayStateJob = scope.launch {
                    attached.state.collect { state ->
                        Log.i(TAG, "Glasses display state: $state")
                        displayStarted = state == DisplayState.STARTED
                        if (displayStarted) sendPendingScreenIfReady()
                    }
                }
            }
            .onFailure { error, _ ->
                Log.w(TAG, "Unable to attach glasses display: ${error.description}")
            }
    }

    private fun sendPendingScreenIfReady() {
        if (!enabled || !displayStarted) return
        val screen = pendingScreen ?: return
        val activeDisplay = display ?: return
        if (screen == lastSentScreen) return
        lastSentScreen = screen
        scope.launch {
            activeDisplay
                .sendContent {
                    flexBox(direction = Direction.COLUMN, gap = 16, padding = 24) {
                        text(screen.title, style = TextStyle.HEADING)
                        screen.subtitle?.let { subtitle ->
                            text(subtitle, style = TextStyle.BODY, color = TextColor.SECONDARY)
                        }
                        // A buttonGroup with no buttons would put an empty
                        // container on a 600x600 screen; the status line above
                        // carries the screen on its own instead.
                        if (screen.items.isNotEmpty()) {
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
                }
                .onSuccess {
                    Log.i(
                        TAG,
                        "Rendered glasses screen '${screen.title}' (${screen.items.size} buttons)"
                    )
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
        displayStateJob?.cancel()
        displayStateJob = null
        displayStarted = false
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
