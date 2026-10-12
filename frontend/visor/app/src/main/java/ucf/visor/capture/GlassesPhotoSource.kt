package ucf.visor.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.meta.wearable.dat.camera.Camera
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.types.PhotoData
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.SpecificDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.DeviceSessionError
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import ucf.visor.tts.SpokenPhrases
import ucf.visor.wearables.GlassesAppUpdate
import ucf.visor.wearables.GlassesLease
import ucf.visor.wearables.describeSessionError
import ucf.visor.wearables.preferredDevice

/**
 * Takes one photo with the glasses camera for each request.
 *
 * MWDAT allows one `DeviceSession` per device, which VISOR shares through
 * [GlassesLease] (glasses tap navigation and the head-motion lab use it too).
 * So every capture takes the lease, opens a session on the pair the wearer is
 * using ([preferredDevice]), takes the picture, and then closes the session
 * and gives the lease back — the tap navigation reconnects on its own once the
 * lease is free. Not keeping the stream open between requests also keeps the
 * glasses camera off while nobody is asking a question.
 *
 * The photo is taken from the camera stream, so it is only as sharp as the
 * stream: [capturePhoto] uses [DEFAULT_STREAM], and [forReading] the sharper
 * [READING_STREAM] that small print needs.
 *
 * Every failure is reported as a sentence fit to speak aloud.
 */
class GlassesPhotoSource(
    private val requestPermission: suspend (Permission) -> PermissionStatus,
) : PhotoSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // MWDAT results carry no request id, so only one capture may be in flight.
    private val captureLock = Mutex()

    override fun capturePhoto(onPhoto: (Bitmap) -> Unit, onError: (String) -> Unit) =
        launchCapture(DEFAULT_STREAM, onPhoto, onError)

    /** The same camera, streaming at [READING_STREAM]; it waits its turn with [capturePhoto]. */
    val forReading: PhotoSource = object : PhotoSource {
        override fun capturePhoto(onPhoto: (Bitmap) -> Unit, onError: (String) -> Unit) =
            launchCapture(READING_STREAM, onPhoto, onError)
    }

    private fun launchCapture(
        configuration: StreamConfiguration,
        onPhoto: (Bitmap) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            captureLock.withLock {
                try {
                    capture(configuration, onPhoto, onError)
                } catch (e: Exception) {
                    Log.e(TAG, "Glasses camera failed", e)
                    onError(SpokenPhrases.GLASSES_CAMERA_UNAVAILABLE)
                }
            }
        }
    }

    private suspend fun capture(
        configuration: StreamConfiguration,
        onPhoto: (Bitmap) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (Wearables.checkPermissionStatus(Permission.CAMERA) != PermissionStatus.Granted &&
            requestPermission(Permission.CAMERA) != PermissionStatus.Granted
        ) {
            onError(SpokenPhrases.NO_CAMERA_PERMISSION)
            return
        }

        val deviceId = preferredDevice()?.first
            ?: return onError(describeSessionError(DeviceSessionError.NO_ELIGIBLE_DEVICE))

        if (!GlassesLease.acquire(LEASE_OWNER)) {
            onError(describeSessionError(DeviceSessionError.SESSION_ALREADY_EXISTS))
            return
        }

        var session: DeviceSession? = null
        var camera: Camera? = null
        var errorJob: Job? = null
        try {
            val opened = createSessionWithRetry(deviceId)
                ?: return onError(SpokenPhrases.GLASSES_CAMERA_UNAVAILABLE)
            session = opened
            var lastError: DeviceSessionError? = null
            errorJob = scope.launch {
                opened.errors.collect { error ->
                    Log.w(TAG, "session error ${error.name}: ${error.description}")
                    lastError = error
                    GlassesAppUpdate.report(error)
                }
            }
            opened.start()
            withTimeoutOrNull(START_TIMEOUT_MS) {
                opened.state.first { it == DeviceSessionState.STARTED }
            } ?: return onError(
                lastError?.let(::describeSessionError) ?: SpokenPhrases.GLASSES_CAMERA_UNAVAILABLE
            )
            GlassesAppUpdate.onSessionStarted()

            val attached = opened.addCamera(configuration).getOrThrow()
            camera = attached
            val stream = attached.stream
            stream.start()
            withTimeout(START_TIMEOUT_MS) {
                stream.state.first { it == StreamState.STREAMING }
            }

            stream.capturePhoto()
                .onSuccess { data ->
                    val bitmap = toBitmap(data)
                    if (bitmap != null) onPhoto(bitmap)
                    else onError(SpokenPhrases.GLASSES_PHOTO_UNREADABLE)
                }
                .onFailure { error, _ ->
                    Log.e(TAG, "capturePhoto failed: $error")
                    onError(SpokenPhrases.GLASSES_PHOTO_FAILED)
                }
        } finally {
            errorJob?.cancel()
            // camera.stop() also detaches the capability from the session.
            camera?.stop()
            session?.stop()
            GlassesLease.release(LEASE_OWNER)
        }
    }

    private suspend fun createSessionWithRetry(deviceId: DeviceIdentifier): DeviceSession? {
        // The tap navigation gives up its session when we take the lease, but
        // its stop is asynchronous: SESSION_ALREADY_EXISTS in that window is
        // worth waiting out (same approach as LabGlassesLink).
        repeat(SESSION_ATTEMPTS) { attempt ->
            val result = Wearables.createSession(SpecificDeviceSelector(deviceId))
            result.getOrNull()?.let { return it }
            val error = result.errorOrNull()
            Log.w(TAG, "createSession attempt ${attempt + 1} failed: ${error?.name}")
            error?.let(GlassesAppUpdate::report)
            if (error != DeviceSessionError.SESSION_ALREADY_EXISTS) return null
            delay(SESSION_RETRY_MS)
        }
        return null
    }

    private fun toBitmap(data: PhotoData): Bitmap? = when (data) {
        is PhotoData.Bitmap -> data.bitmap
        is PhotoData.HEIC -> {
            val buffer = data.data
            val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    fun close() {
        scope.cancel()
        GlassesLease.release(LEASE_OWNER)
    }

    private companion object {
        const val TAG = "VisorGlassesCamera"
        const val LEASE_OWNER = "glasses_camera"
        const val START_TIMEOUT_MS = 15_000L
        const val SESSION_ATTEMPTS = 20
        const val SESSION_RETRY_MS = 250L

        // Identifying objects and describing scenes: 504 x 896, quick to send.
        val DEFAULT_STREAM = StreamConfiguration(videoQuality = VideoQuality.MEDIUM, frameRate = 15)

        // Reading text: Meta's sharpest stream (720 x 1280). The low frame rate
        // gives each frame more of the Bluetooth bandwidth, so it is compressed
        // less, which Meta's docs recommend for image quality.
        val READING_STREAM = StreamConfiguration(videoQuality = VideoQuality.HIGH, frameRate = 7)
    }
}
