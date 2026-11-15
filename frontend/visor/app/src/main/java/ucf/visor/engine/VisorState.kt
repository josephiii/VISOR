package ucf.visor.engine

import android.content.Context
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.selectors.DeviceSelector
import com.meta.wearable.dat.core.types.Device
import com.meta.wearable.dat.core.types.DeviceCompatibility
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ucf.visor.wearables.GlassesConnectionNotice
import ucf.visor.data.ProfileStore
import ucf.visor.data.SessionStore
import ucf.visor.data.UserProfile
import ucf.visor.network.Connectivity
import ucf.visor.stt.CameraCommand
import ucf.visor.stt.MicRoute
import ucf.visor.ui.viewmodel.CaptureStatus
import ucf.visor.ui.viewmodel.LastAnswer
import ucf.visor.wearables.GlassesAppUpdate
import ucf.visor.wearables.GlassesStatus
import ucf.visor.wearables.rankForSession
import ucf.visor.wearables.toGlassesStatus

/**
 * What VISOR knows that outlives any one screen: the profile, whether someone
 * is logged in, whether the phone is online, the glasses, and what each camera
 * feature is doing and last answered.
 *
 * One per process (see VisorApp). VisorViewModel shows it on screen;
 * VisorEngine acts on it whether or not a screen is showing, which is what lets
 * VISOR keep answering with the screen off (background listening).
 */
class VisorState(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val sessionStore = SessionStore(context)
    val profileStore = ProfileStore(context)
    private val connectivity = Connectivity(context)

    private val _profile = MutableStateFlow(profileStore.load())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    fun updateProfile(profile: UserProfile) {
        _profile.value = profile
        profileStore.save(profile)
    }

    /** After the account is deleted: back to the defaults, saved nowhere. */
    fun resetProfile() {
        profileStore.clear()
        _profile.value = UserProfile()
    }

    private val _loggedIn = MutableStateFlow(sessionStore.isLoggedIn())

    /** Whether someone is logged in, as saved on the phone. */
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    fun isLoggedIn(): Boolean = _loggedIn.value

    /** Call after logging in or out ([sessionStore] changed), so [loggedIn] follows. */
    fun onSessionChanged() {
        _loggedIn.value = sessionStore.isLoggedIn()
    }

    // Online or not from the start, so a phone that is offline at launch
    // shows its locked features at once, not after a callback.
    private val _isOnline = MutableStateFlow(connectivity.isOnlineNow())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        scope.launch { connectivity.changes().collect { _isOnline.value = it } }
    }

    // ---------------------------------------------------------------------
    // Camera features

    private val _captures = MutableStateFlow<Map<CameraCommand, CaptureStatus>>(emptyMap())

    /**
     * What each camera feature is doing (see [onCaptureStarted]), for its
     * button on Home. A feature that isn't working isn't in it: it is idle.
     */
    val captures: StateFlow<Map<CameraCommand, CaptureStatus>> = _captures.asStateFlow()

    private val _lastAnswer = MutableStateFlow<LastAnswer?>(null)

    /** What a camera feature last answered, for Home's Last answer card and the notification. */
    val lastAnswer: StateFlow<LastAnswer?> = _lastAnswer.asStateFlow()

    // A wake phrase and Home's button can ask the same feature for a second
    // picture before the first is answered; it is working until each is.
    private val capturesInFlight = mutableMapOf<CameraCommand, Int>()

    /** [command] has been asked for: VISOR is taking its picture. Call on the main thread. */
    fun onCaptureStarted(command: CameraCommand) {
        capturesInFlight[command] = (capturesInFlight[command] ?: 0) + 1
        _captures.update { it + (command to CaptureStatus.TAKING_PICTURE) }
    }

    /** The glasses have taken [command]'s picture: VISOR is working out what is in it. */
    fun onPictureTaken(command: CameraCommand) {
        _captures.update {
            if (it[command] == CaptureStatus.TAKING_PICTURE) it + (command to CaptureStatus.THINKING) else it
        }
    }

    /** [command] has answered with [text], which VISOR is now saying. */
    fun onAnswered(command: CameraCommand, text: String) {
        val left = ((capturesInFlight[command] ?: 0) - 1).coerceAtLeast(0)
        if (left == 0) {
            capturesInFlight.remove(command)
            _captures.update { it - command }
        } else {
            capturesInFlight[command] = left
        }
        showAnswer(text, command)
    }

    /** Shows [text] on Home's Last answer card, as an answer [command] has just given. */
    fun showAnswer(text: String, command: CameraCommand) {
        _lastAnswer.value = LastAnswer(text, System.currentTimeMillis(), command)
    }

    /** It may be text read off someone's mail: not for the next person to log in. */
    fun clearLastAnswer() {
        _lastAnswer.value = null
    }

    // ---------------------------------------------------------------------
    // Listening (written by VisorEngine, shown in Settings)

    private val _listeningThrough = MutableStateFlow(MicRoute.Source.PHONE)

    /** Which microphone VISOR hears commands through right now: the glasses' or the phone's. */
    val listeningThrough: StateFlow<MicRoute.Source> = _listeningThrough.asStateFlow()

    fun setListeningThrough(source: MicRoute.Source) {
        _listeningThrough.value = source
    }

    private val _glassesConnection = MutableStateFlow(GlassesConnectionNotice.NONE)

    /**
     * What VISOR is doing about a lost link to the glasses (VisorEngine): shown
     * at the top of every screen and on the listening notification.
     */
    val glassesConnection: StateFlow<GlassesConnectionNotice> = _glassesConnection.asStateFlow()

    fun setGlassesConnection(notice: GlassesConnectionNotice) {
        _glassesConnection.value = notice
    }

    private val _backgroundListeningActive = MutableStateFlow(false)

    /** Whether the background listening service is running, so VISOR hears wake phrases with the screen off. */
    val backgroundListeningActive: StateFlow<Boolean> = _backgroundListeningActive.asStateFlow()

    fun setBackgroundListeningActive(active: Boolean) {
        _backgroundListeningActive.value = active
    }

    // ---------------------------------------------------------------------
    // Problems to show at the top of the screen

    private val _errors = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Problems to show at the top of the screen, as they happen. Not replayed:
     * one that happened with no screen showing was already said aloud, and
     * would be stale by the time VISOR is opened.
     */
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    fun reportError(error: String) {
        _errors.tryEmit(error)
    }

    // ---------------------------------------------------------------------
    // MWDAT: the glasses

    private var selector: DeviceSelector? = null

    /**
     * AutoDeviceSelector automatically selects the first available wearable
     * device. Made again after the SDK is restarted ([restartMonitoring]): the
     * old one belongs to the SDK instance that was reset.
     */
    val deviceSelector: DeviceSelector
        get() = selector ?: AutoDeviceSelector().also { selector = it }

    private val _registrationState = MutableStateFlow(RegistrationState.UNAVAILABLE)
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    private val _devices = MutableStateFlow<List<DeviceIdentifier>>(emptyList())
    val devices: StateFlow<List<DeviceIdentifier>> = _devices.asStateFlow()

    private val _glassesStatus = MutableStateFlow<GlassesStatus?>(null)

    /** The pair VISOR would use (worn and connected first), or null when it knows none. */
    val glassesStatus: StateFlow<GlassesStatus?> = _glassesStatus.asStateFlow()

    private val _isFirmwareUpdateRequired = MutableStateFlow(false)
    val isFirmwareUpdateRequired: StateFlow<Boolean> = _isFirmwareUpdateRequired.asStateFlow()

    private val _isDatAppUpdateRequired = MutableStateFlow(false)
    val isDatAppUpdateRequired: StateFlow<Boolean> = _isDatAppUpdateRequired.asStateFlow()

    private var monitoringStarted = false
    private val monitoringJobs = mutableListOf<Job>()
    private val deviceMonitoringJobs = mutableMapOf<DeviceIdentifier, Job>()
    private val deviceSnapshots = mutableMapOf<DeviceIdentifier, Device>()

    /** Watches registration and the glasses. Once `Wearables.initialize` has run; safe to repeat. */
    fun startMonitoring() {
        if (monitoringStarted) return
        monitoringStarted = true

        // This allows the app to react to registration changes (registered, unregistered, etc.)
        monitoringJobs += scope.launch { Wearables.registrationState.collect { _registrationState.value = it } }
        // Reported by whichever VISOR feature's glasses session was refused.
        monitoringJobs += scope.launch { GlassesAppUpdate.required.collect { _isDatAppUpdateRequired.value = it } }
        // This automatically updates when devices are discovered, connected, or disconnected
        monitoringJobs += scope.launch {
            Wearables.devices.collect { value ->
                _devices.value = value.toList()
                monitorDevices(value)
            }
        }
    }

    /**
     * Watches the glasses again from scratch, after the SDK was restarted
     * (VisorEngine.restartWearablesSdk): what was collected belongs to the old instance.
     */
    fun restartMonitoring() {
        monitoringJobs.forEach { it.cancel() }
        monitoringJobs.clear()
        deviceMonitoringJobs.values.forEach { it.cancel() }
        deviceMonitoringJobs.clear()
        deviceSnapshots.clear()
        selector = null
        monitoringStarted = false
        onDevicesChanged()
        startMonitoring()
    }

    private fun monitorDevices(devices: Set<DeviceIdentifier>) {
        // Stop watching devices that are no longer in the list
        (deviceMonitoringJobs.keys - devices).forEach { deviceId ->
            deviceMonitoringJobs.remove(deviceId)?.cancel()
            deviceSnapshots.remove(deviceId)
        }
        onDevicesChanged()

        // Battery, worn, hinge and thermal state arrive on `Device` itself since
        // MWDAT 1.0 (`Wearables.getDeviceState` was removed).
        (devices - deviceMonitoringJobs.keys).forEach { deviceId ->
            deviceMonitoringJobs[deviceId] = scope.launch {
                Wearables.devicesMetadata[deviceId]?.collect { metadata ->
                    deviceSnapshots[deviceId] = metadata
                    onDevicesChanged()
                    if (metadata.compatibility == DeviceCompatibility.DEVICE_UPDATE_REQUIRED) {
                        val deviceName = metadata.name.ifEmpty { deviceId }
                        reportError("Device '$deviceName' requires an update to work with this app")
                    }
                }
            }
        }
    }

    private fun onDevicesChanged() {
        _isFirmwareUpdateRequired.value =
            deviceSnapshots.values.any { it.compatibility == DeviceCompatibility.DEVICE_UPDATE_REQUIRED }
        // Only the pair VISOR would actually use — worn and connected first — is surfaced.
        _glassesStatus.value = deviceSnapshots.values.minByOrNull(::rankForSession)?.toGlassesStatus()
    }
}
