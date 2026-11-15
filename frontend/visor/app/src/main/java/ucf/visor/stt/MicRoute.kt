package ucf.visor.stt

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which microphone VISOR hears commands through: the glasses' when they are
 * connected, otherwise the phone's.
 *
 * Android records from the phone's own microphone even while Bluetooth
 * glasses are connected; their microphone is only used once the phone talks to
 * them over Bluetooth HFP, as for a call. This turns that on, as Meta's
 * "Microphones and speakers" guide does: the glasses become the communication
 * device and the phone enters communication mode. The glasses then beamform to
 * the wearer's voice, turning other sounds and other people down, which is
 * what lets wake phrases work with the phone in a pocket.
 *
 * The cost, while it is on: the glasses can't play high-quality (A2DP) audio,
 * so VISOR's speech and any music through them is mono voice quality. That is
 * why Settings can turn it off (UserProfile.listenThroughGlasses).
 *
 * [onRouteChanged] is called whenever a Bluetooth device appears or goes, the
 * communication device changes, or a phone call ends, so the owner can call
 * [update] again.
 *
 * When the glasses' microphone is taken away from VISOR, it waits before taking
 * it back, and after a few drops in a row it stays on the phone for a while
 * ([RouteBackoff]), calling [onGaveUp] so the wearer can be told. Fighting
 * over the route makes the glasses chime and can unsettle their Bluetooth link.
 */
class MicRoute(
    context: Context,
    private val onRouteChanged: () -> Unit,
    private val onGaveUp: () -> Unit = {},
) {
    enum class Source { PHONE, GLASSES }

    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _source = MutableStateFlow(Source.PHONE)

    /** The microphone commands are heard through right now. */
    val source: StateFlow<Source> = _source.asStateFlow()

    /** The glasses VISOR holds as the communication device, or null when it holds none. */
    private var held: AudioDeviceInfo? = null
    private var changedAt = 0L

    private val backoff = RouteBackoff()
    private val retry = Runnable { onRouteChanged() }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = onRouteChanged()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = onRouteChanged()
    }

    private val communicationListener = AudioManager.OnCommunicationDeviceChangedListener { device ->
        // Taken away (the link dropped, a call, another app): back off, then let the owner decide again.
        if (held != null && device?.id != held?.id) {
            Log.i(TAG, "Communication device changed to ${device?.productName}; route released")
            held = null
            changedAt = SystemClock.elapsedRealtime()
            _source.value = Source.PHONE
            // VISOR no longer has the glasses: withdraw its request for them and
            // leave the call mode it set, whatever Android fell back to. Left in
            // call mode with the phone itself as the device (field log,
            // 2026-11-15: "Communication device changed to B1680V", the phone),
            // VISOR's voice came out of the earpiece, too quiet to hear.
            audio.clearCommunicationDevice()
            if (audio.mode == AudioManager.MODE_IN_COMMUNICATION) audio.mode = AudioManager.MODE_NORMAL
            if (backoff.onDropped(SystemClock.elapsedRealtime())) {
                Log.w(TAG, "The glasses' microphone keeps dropping; using the phone's for a while")
                onGaveUp()
            }
            onRouteChanged()
        }
    }

    // A phone call takes over the audio mode; once it ends, take the glasses back.
    private val modeListener = AudioManager.OnModeChangedListener { mode ->
        if (mode == AudioManager.MODE_NORMAL) onRouteChanged()
    }

    init {
        audio.registerAudioDeviceCallback(deviceCallback, main)
        audio.addOnCommunicationDeviceChangedListener(context.mainExecutor, communicationListener)
        audio.addOnModeChangedListener(context.mainExecutor, modeListener)
    }

    /**
     * Listens through the glasses named [glassesName] when [useGlasses], or
     * through the phone otherwise. Safe to call as often as anything changes.
     */
    fun update(useGlasses: Boolean, glassesName: String?) {
        if (!useGlasses) return release()
        // A call has the audio: leave it be, and take the glasses back after (modeListener).
        if (audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_RINGTONE) {
            _source.value = Source.PHONE
            return
        }
        val glasses = findGlasses(glassesName) ?: return release()
        if (held?.id == glasses.id) return

        // Just taken away, or taken away too often: not yet. Look again when it is time.
        val wait = backoff.waitMs(SystemClock.elapsedRealtime())
        if (wait > 0) {
            _source.value = Source.PHONE
            main.removeCallbacks(retry)
            main.postDelayed(retry, wait)
            return
        }

        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        if (audio.setCommunicationDevice(glasses)) {
            held = glasses
            changedAt = SystemClock.elapsedRealtime()
            _source.value = Source.GLASSES
            Log.i(TAG, "Listening through ${glasses.productName}")
        } else {
            Log.w(TAG, "Couldn't route the microphone to ${glasses.productName}")
            audio.mode = AudioManager.MODE_NORMAL
            _source.value = Source.PHONE
        }
    }

    /**
     * Lets go of the glasses' microphone for [forMs], so the glasses camera
     * can work: their call link open as a session starts can end the session
     * on some firmware (Meta, meta-wearables-dat-android #192).
     */
    fun yieldToCamera(forMs: Long) {
        backoff.blockFor(SystemClock.elapsedRealtime(), forMs)
        release()
        main.removeCallbacks(retry)
        main.postDelayed(retry, forMs)
    }

    /** The wearer asked for the glasses' microphone again (Settings): try at once. */
    fun resetBackoff() {
        backoff.reset()
        main.removeCallbacks(retry)
    }

    /**
     * Whether the glasses named [glassesName] are connected to the phone for
     * audio, whatever the Meta Wearables SDK thinks: for telling an SDK that
     * has lost its own link to them from glasses that are really off.
     */
    fun glassesConnected(glassesName: String?): Boolean =
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
            device.type in BLUETOOTH_AUDIO && GlassesNames.matches(device.productName?.toString(), glassesName)
        }

    /** Back to the phone's microphone, and out of communication mode. */
    fun release() {
        if (held != null) {
            held = null
            changedAt = SystemClock.elapsedRealtime()
            audio.clearCommunicationDevice()
            // Only undo the mode VISOR set: a call may have its own by now.
            if (audio.mode == AudioManager.MODE_IN_COMMUNICATION) audio.mode = AudioManager.MODE_NORMAL
            Log.i(TAG, "Listening through the phone")
        }
        _source.value = Source.PHONE
    }

    /**
     * The glasses' microphone as an input, for [android.media.AudioRecord.setPreferredDevice],
     * or null to use the phone's. Asking for it by device, not only through the
     * communication route, keeps the wake-phrase listener on the glasses even if
     * the route is still settling.
     */
    fun preferredInput(): AudioDeviceInfo? {
        val glasses = held ?: return null
        val inputs = audio.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        return inputs.firstOrNull { it.address == glasses.address } ?: inputs.firstOrNull()
    }

    /**
     * Waits until a change of microphone has settled (up to [SETTLE_MS]).
     * Meta: the HFP route must settle before a glasses camera stream starts,
     * or the audio route can fail silently.
     */
    suspend fun awaitSettled() {
        val left = changedAt + SETTLE_MS - SystemClock.elapsedRealtime()
        if (left > 0) delay(left)
    }

    /**
     * The glasses among the phone's Bluetooth call devices: the one named like
     * the pair MWDAT reports, or else the only one there is. Another headset
     * with a different name is never taken for the glasses.
     */
    private fun findGlasses(glassesName: String?): AudioDeviceInfo? {
        val sco = audio.availableCommunicationDevices
            .filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        if (sco.isEmpty()) return null
        val named = sco.firstOrNull { device -> GlassesNames.matches(device.productName?.toString(), glassesName) }
        return named ?: sco.singleOrNull()
    }

    private companion object {
        const val TAG = "VisorMicRoute"
        const val SETTLE_MS = 2_000L
        val BLUETOOTH_AUDIO = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    }
}

/**
 * When VISOR may take the glasses' microphone back after losing it. Pure, with
 * the clock passed in, for unit tests (RouteBackoffTest).
 *
 * After each drop it waits [regrabDelayMs]. After [maxDrops] drops within
 * [windowMs], it gives up for [giveUpMs] and stays on the phone's microphone.
 */
class RouteBackoff(
    private val regrabDelayMs: Long = 5_000L,
    private val maxDrops: Int = 3,
    private val windowMs: Long = 60_000L,
    private val giveUpMs: Long = 5 * 60_000L,
) {
    private val drops = ArrayDeque<Long>()
    private var blockedUntil = 0L

    /** The route was taken away at [now]. Returns true when VISOR gives up for a while. */
    fun onDropped(now: Long): Boolean {
        drops.addLast(now)
        while (drops.isNotEmpty() && now - drops.first() > windowMs) drops.removeFirst()
        return if (drops.size >= maxDrops) {
            drops.clear()
            blockedUntil = now + giveUpMs
            true
        } else {
            blockedUntil = maxOf(blockedUntil, now + regrabDelayMs)
            false
        }
    }

    /** Keeps VISOR off the route until [ms] after [now], whatever the drops. */
    fun blockFor(now: Long, ms: Long) {
        blockedUntil = maxOf(blockedUntil, now + ms)
    }

    /** How long until VISOR may take the route again; 0 for now. */
    fun waitMs(now: Long): Long = (blockedUntil - now).coerceAtLeast(0L)

    fun reset() {
        drops.clear()
        blockedUntil = 0L
    }
}

/** Whether a Bluetooth device's name is the glasses MWDAT reports. Pure, for unit tests. */
object GlassesNames {
    private val META_WORDS = listOf("ray-ban", "rayban", "meta", "oakley")

    fun matches(bluetoothName: String?, glassesName: String?): Boolean {
        val name = bluetoothName?.trim()?.lowercase().orEmpty()
        if (name.isEmpty()) return false
        val wanted = glassesName?.trim()?.lowercase().orEmpty()
        if (wanted.isNotEmpty() && (name.contains(wanted) || wanted.contains(name))) return true
        return META_WORDS.any { name.contains(it) }
    }
}
