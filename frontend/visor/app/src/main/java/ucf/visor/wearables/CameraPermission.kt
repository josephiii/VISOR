package ucf.visor.wearables

import android.util.Log
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.DeviceSessionError
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionError
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.WearablesException
import ucf.visor.tts.SpokenPhrases

/**
 * Makes sure VISOR may use the glasses camera, asking the wearer through the
 * Meta AI app ([requestPermission]) when it may not yet. Returns null when it
 * may, or else a sentence fit to speak aloud saying why not.
 *
 * Used before every glasses photo and before a session starts. MWDAT answers
 * the check with a `DatResult`, which never equals a `PermissionStatus`: only
 * what is inside it (`getOrNull()`) can be compared.
 */
suspend fun cameraPermissionProblem(
    requestPermission: suspend (Permission) -> PermissionStatus,
): String? {
    val check = try {
        Wearables.checkPermissionStatus(Permission.CAMERA)
    } catch (e: WearablesException) {
        // MainActivity initializes the SDK once VISOR's phone permissions are granted.
        Log.w(TAG, "Camera permission checked before the SDK was initialized", e)
        return "VISOR can't reach your glasses until you allow its permissions on this phone."
    }

    // Asking would fail for the same reason the check did (no glasses, no Meta AI app...).
    check.errorOrNull()?.let { error ->
        Log.w(TAG, "Camera permission check failed: ${error.description}")
        return describePermissionError(error)
    }

    return when {
        check.getOrNull() == PermissionStatus.Granted -> null
        requestPermission(Permission.CAMERA) == PermissionStatus.Granted -> null
        else -> SpokenPhrases.NO_CAMERA_PERMISSION
    }
}

private fun describePermissionError(error: PermissionError): String = when (error) {
    PermissionError.NO_DEVICE,
    PermissionError.NO_DEVICE_WITH_CONNECTION ->
        describeSessionError(DeviceSessionError.NO_ELIGIBLE_DEVICE)
    PermissionError.META_AI_NOT_INSTALLED ->
        "VISOR needs the Meta AI app to use your glasses. Please install it."
    PermissionError.REQUEST_IN_PROGRESS ->
        "VISOR is already asking for camera access. Answer it in the Meta AI app."
    PermissionError.CONNECTION_ERROR,
    PermissionError.REQUEST_TIMEOUT,
    PermissionError.INTERNAL_ERROR ->
        "I couldn't check camera access on your glasses. Try again in a moment."
}

private const val TAG = "VisorCameraPermission"
