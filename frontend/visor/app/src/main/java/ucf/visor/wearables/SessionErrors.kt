package ucf.visor.wearables

import com.meta.wearable.dat.core.types.DeviceSessionError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the Device Access Toolkit app on the glasses needs an update.
 *
 * MWDAT only says so through a session error
 * ([DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED]), and three
 * VISOR features open sessions (the glasses camera, tap navigation and the
 * head-motion lab), so each reports its errors here. The Pair Glasses screen
 * watches [required] to offer its "Update app on glasses" button, which Meta's
 * docs prescribe for this error. A session that starts clears it.
 */
object GlassesAppUpdate {

    private val _required = MutableStateFlow(false)
    val required: StateFlow<Boolean> = _required.asStateFlow()

    fun report(error: DeviceSessionError) {
        if (error == DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED) _required.value = true
    }

    fun onSessionStarted() {
        _required.value = false
    }
}

/**
 * MWDAT 1.0 added a session error that is *not* an ending:
 * [DeviceSessionError.DWA_OUT_OF_STU_RANGE] is a compatibility warning the
 * SDK says apps can continue through. Everything else on `session.errors`
 * either ends the session or means it could not start.
 */
val DeviceSessionError.isWarningOnly: Boolean
    get() = this == DeviceSessionError.DWA_OUT_OF_STU_RANGE

/**
 * A plain-language sentence for a session error, suitable for speaking aloud.
 *
 * VISOR's users may not be looking at the screen when the glasses drop out, so
 * these say what happened and what to do, in words that make sense read by
 * TTS — no enum names, no jargon. The SDK's own `description` is still logged
 * alongside for developers.
 */
fun describeSessionError(error: DeviceSessionError): String = when (error) {
    DeviceSessionError.NO_ELIGIBLE_DEVICE ->
        "No connected glasses were found. Make sure your glasses are on, unfolded and connected in the Meta AI app."
    DeviceSessionError.DEVICE_DISCONNECTED ->
        "The glasses disconnected."
    DeviceSessionError.SESSION_ENDED_BY_DEVICE ->
        "The glasses ended the session."
    DeviceSessionError.SESSION_ALREADY_EXISTS ->
        "The glasses are busy with another VISOR session. Try again in a moment."
    DeviceSessionError.SESSION_ALREADY_STOPPED,
    DeviceSessionError.SESSION_IDLE ->
        "The glasses session was not running."
    DeviceSessionError.CAPABILITY_DENIED ->
        "This feature has not been approved for VISOR in the Meta Wearables Developer Center."
    DeviceSessionError.CAPABILITY_ALREADY_ADDED,
    DeviceSessionError.CAPABILITY_NOT_FOUND ->
        "The glasses feature could not be set up. Try again."
    DeviceSessionError.THERMAL_CRITICAL,
    DeviceSessionError.THERMAL_EMERGENCY ->
        "The glasses are too hot and stopped. Let them cool down before trying again."
    DeviceSessionError.PEAK_POWER_SHUTDOWN ->
        "The glasses stopped to protect their battery."
    DeviceSessionError.BATTERY_CRITICAL ->
        "The glasses' battery is too low. Please charge them."
    DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED ->
        "The glasses need a software update from the Meta AI app."
    DeviceSessionError.INSUFFICIENT_SDK_VERSION ->
        "This version of VISOR is too old for your glasses. Please update VISOR."
    DeviceSessionError.DWA_OUT_OF_STU_RANGE ->
        "An update for the glasses is recommended, but VISOR can keep going."
    DeviceSessionError.DWA_UNAVAILABLE ->
        "The glasses' app service is not available right now. Try again in a moment."
    DeviceSessionError.UNEXPECTED_ERROR ->
        "Something went wrong with the glasses connection."
}
