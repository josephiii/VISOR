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
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.types.DeviceSessionError
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ucf.visor.tts.SpokenPhrases
import ucf.visor.wearables.GlassesRecovery
import ucf.visor.wearables.HeldGlassesSession
import ucf.visor.wearables.cameraPermissionProblem
import ucf.visor.wearables.describeSessionError
import ucf.visor.wearables.preferredDevice

/**
 * Takes one photo with the glasses camera for each request, inside the glasses
 * session VISOR holds while it is in use ([HeldGlassesSession]).
 *
 * The camera stream is kept running for [CAMERA_LINGER_MS] after a picture,
 * so a run of questions shares one stream, and stopped after that. Every
 * stream that starts or stops hands the glasses' fast Bluetooth data channel
 * between VISOR and the Meta AI app, and on MWDAT 1.0.0 a handoff can cut the
 * SDK's link to the glasses for good (field log, 2026-11-13: the link died
 * 4 s after a Read Text stream stopped). One handoff per run of questions,
 * instead of one per picture. The glasses' capture light shows while the
 * stream runs, as it should. One session per picture cost the link too: see
 * HeldGlassesSession.
 *
 * The photo is taken from the camera stream, so it is only as sharp as the
 * stream: [capturePhoto] uses [DEFAULT_STREAM], and [forReading] the sharper
 * [READING_STREAM] that small print needs.
 *
 * Every failure is reported as a sentence fit to speak aloud.
 */
class GlassesPhotoSource(
    private val session: HeldGlassesSession,
    private val requestPermission: suspend (Permission) -> PermissionStatus,
    /**
     * Called when a picture failed in a way worth one recovery (see
     * [Problem.linkLost]): whether to try the picture once more, or what to say.
     */
    private val recoverLink: suspend () -> GlassesRecovery.Outcome = { GlassesRecovery.Outcome.Stop() },
) : PhotoSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Where cameras are stopped: off the main thread, and not cancelled with [scope]. */
    private val stopScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // MWDAT results carry no request id, so only one capture may be in flight.
    private val captureLock = Mutex()

    /** The camera kept streaming between pictures, the session it is in, and when it stops. */
    private var activeCamera: Camera? = null
    private var activeSession: DeviceSession? = null
    private var lingerJob: Job? = null

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

    /** Why a picture could not be taken, said to the wearer as [spoken]. */
    private data class Problem(
        val spoken: String,
        /**
         * The glasses or their link failed in a way a recovery may fix: the
         * SDK says there are no connected glasses (which it also says after
         * losing its own link to glasses still connected to the phone), the
         * session would not start, or the stream never came. Worth one
         * recovery (VisorEngine.recoverGlassesLink) and one more try.
         */
        val linkLost: Boolean = false,
    )

    private suspend fun capture(
        configuration: StreamConfiguration,
        onPhoto: (Bitmap) -> Unit,
        onError: (String) -> Unit,
    ) {
        val problem = attempt(configuration, onPhoto) ?: return
        if (!problem.linkLost) return onError(problem.spoken)
        Log.i(TAG, "Glasses link problem (${problem.spoken}); recovering")
        when (val outcome = recoverLink()) {
            GlassesRecovery.Outcome.TryAgain -> attempt(configuration, onPhoto)?.let { onError(it.spoken) }
            is GlassesRecovery.Outcome.Stop -> onError(outcome.spoken ?: problem.spoken)
        }
    }

    /** Takes one picture. Returns null once it is delivered to [onPhoto], or why it could not be. */
    private suspend fun attempt(
        configuration: StreamConfiguration,
        onPhoto: (Bitmap) -> Unit,
    ): Problem? {
        cameraPermissionProblem(requestPermission)?.let { spoken ->
            return Problem(spoken, linkLost = spoken == SpokenPhrases.NO_GLASSES_TRY_PHONE)
        }
        if (preferredDevice() == null) return Problem(SpokenPhrases.NO_GLASSES_TRY_PHONE, linkLost = true)

        return when (val result = session.withSession { opened -> takePicture(opened, configuration, onPhoto) }) {
            is HeldGlassesSession.Result.Done -> result.value
            is HeldGlassesSession.Result.Unavailable -> {
                val failure = result.failure
                when {
                    failure.paused -> Problem(SpokenPhrases.GLASSES_PAUSED)
                    else -> Problem(
                        failure.error?.let(::describeSessionError) ?: SpokenPhrases.GLASSES_CAMERA_UNAVAILABLE,
                        linkLost = failure.error == null || failure.error in LINK_LOST_ERRORS,
                    )
                }
            }
        }
    }

    /**
     * One photo from the camera streaming in [opened]: the one kept from the
     * last picture when it is still streaming, or a new one.
     */
    private suspend fun takePicture(
        opened: DeviceSession,
        @Suppress("UNUSED_PARAMETER") configuration: StreamConfiguration,
        onPhoto: (Bitmap) -> Unit,
    ): Problem? {
        lingerJob?.cancel()
        val camera = streamingCamera(opened) ?: return Problem(SpokenPhrases.GLASSES_CAMERA_UNAVAILABLE, linkLost = true)

        var problem: Problem? = null
        camera.stream.capturePhoto()
            .onSuccess { data ->
                val bitmap = toBitmap(data)
                if (bitmap != null) onPhoto(bitmap)
                else problem = Problem(SpokenPhrases.GLASSES_PHOTO_UNREADABLE)
            }
            .onFailure { error, _ ->
                Log.e(TAG, "capturePhoto failed: $error")
                problem = Problem(SpokenPhrases.GLASSES_PHOTO_FAILED)
            }
        // A camera that failed is not kept; one that worked streams on for a while.
        if (problem != null) stopActiveCamera() else scheduleCameraStop()
        return problem
    }

    /** The kept camera if it is still streaming in [opened], or a newly started one, or null. */
    private suspend fun streamingCamera(opened: DeviceSession): Camera? {
        val kept = activeCamera
        if (kept != null && activeSession === opened && kept.stream.state.value == StreamState.STREAMING) {
            Log.i(TAG, "Reusing the streaming glasses camera")
            return kept
        }
        stopActiveCamera()
        // Meta's calls can block on a failing Bluetooth link: never on the main thread.
        val attached = withContext(Dispatchers.IO) { opened.addCamera(SHARED_STREAM) }.getOrNull() ?: return null
        activeCamera = attached
        activeSession = opened
        withContext(Dispatchers.IO) { attached.stream.start() }
        val streaming = withTimeoutOrNull(STREAM_TIMEOUT_MS) {
            attached.stream.state.first { it == StreamState.STREAMING }
        }
        if (streaming == null) {
            stopActiveCamera()
            return null
        }
        return attached
    }

    /** Stops the kept camera [CAMERA_LINGER_MS] from now, unless another picture comes first. */
    private fun scheduleCameraStop() {
        lingerJob?.cancel()
        lingerJob = scope.launch {
            delay(CAMERA_LINGER_MS)
            captureLock.withLock {
                Log.i(TAG, "No more pictures for a while; stopping the glasses camera")
                stopActiveCamera()
            }
        }
    }

    /** Stops the kept camera, if any. */
    private suspend fun stopActiveCamera() {
        val camera = activeCamera ?: return
        activeCamera = null
        activeSession = null
        stopCameraQuietly(camera)
    }

    /**
     * Stops [camera] (which also detaches it from the session) off the main
     * thread, waiting at most [STOP_TIMEOUT_MS]: on a failing link Meta's
     * `stop()` can hang, and on the main thread it froze VISOR (ANR,
     * 2026-11-13). A stop that outlasts the wait carries on by itself.
     */
    private suspend fun stopCameraQuietly(camera: Camera?) {
        camera ?: return
        withContext(NonCancellable) {
            val stopping = stopScope.launch {
                runCatching { camera.stop() }.onFailure { Log.w(TAG, "camera.stop failed", it) }
            }
            withTimeoutOrNull(STOP_TIMEOUT_MS) { stopping.join() }
                ?: Log.w(TAG, "The glasses camera did not stop within $STOP_TIMEOUT_MS ms; carrying on")
        }
    }

    private fun toBitmap(data: PhotoData): Bitmap? = when (data) {
        is PhotoData.Bitmap -> data.bitmap
        is PhotoData.HEIC -> {
            val buffer = data.data
            val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    /** Stops the kept camera now: VISOR is done with the glasses for the moment. */
    fun releaseCamera() {
        lingerJob?.cancel()
        scope.launch { captureLock.withLock { stopActiveCamera() } }
    }

    fun close() {
        scope.cancel()
    }

    private companion object {
        const val TAG = "VisorGlassesCamera"
        const val STREAM_TIMEOUT_MS = 15_000L
        const val STOP_TIMEOUT_MS = 3_000L

        /** How long the camera keeps streaming after a picture, for the next one. */
        const val CAMERA_LINGER_MS = 45_000L

        /** Session errors that mean the SDK no longer sees the glasses (see Problem.linkLost). */
        val LINK_LOST_ERRORS = setOf(
            DeviceSessionError.NO_ELIGIBLE_DEVICE,
            DeviceSessionError.DEVICE_DISCONNECTED,
            DeviceSessionError.SESSION_ENDED_BY_DEVICE,
        )

        // Identifying objects and describing scenes: 504 x 896, quick to send.
        val DEFAULT_STREAM = StreamConfiguration(videoQuality = VideoQuality.MEDIUM, frameRate = 15)

        // Reading text: Meta's sharpest stream (720 x 1280). The low frame rate
        // gives each frame more of the Bluetooth bandwidth, so it is compressed
        // less, which Meta's docs recommend for image quality.
        val READING_STREAM = StreamConfiguration(videoQuality = VideoQuality.HIGH, frameRate = 7)

        /**
         * The one stream every feature shares, so a kept camera serves the next
         * question whatever it is: a different stream would mean stopping one and
         * starting another, the handoff that costs the link. Reading's, since small
         * print needs it most; the other features are as well served by it.
         */
        val SHARED_STREAM = READING_STREAM
    }
}
