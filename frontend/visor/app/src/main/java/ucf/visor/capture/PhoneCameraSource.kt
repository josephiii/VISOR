package ucf.visor.capture

import android.Manifest.permission.CAMERA
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import ucf.visor.tts.SpokenPhrases
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Takes one photo with the phone's back camera for each request: the camera
 * VISOR uses when the user has chosen their phone over their glasses
 * ([ucf.visor.data.CameraChoice]).
 *
 * It works like [GlassesPhotoSource], so every camera feature behaves the same
 * whichever camera took its picture: no viewfinder, one picture per request,
 * and the camera is opened for that picture and closed again straight after,
 * so it is never on while nobody is asking a question.
 *
 * The phone's camera has autofocus and many more pixels than the glasses'
 * stream, so [forReading] asks for more of them, for small print.
 *
 * Android only lets an app use the camera while it is on screen, so a wake
 * phrase heard with VISOR in the background is told to open VISOR. A photo
 * that comes out black ([BlackPhoto]) is not answered at all: VISOR says what
 * is likely covering the camera instead.
 *
 * Every failure is reported as a sentence fit to speak aloud.
 */
class PhoneCameraSource(private val activity: ComponentActivity) : PhotoSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // One camera, one picture at a time; a second request waits its turn.
    private val captureLock = Mutex()

    // Decoding and turning the photo upright, off the main thread.
    private val decoder = Executors.newSingleThreadExecutor()

    override fun capturePhoto(onPhoto: (Bitmap) -> Unit, onError: (String) -> Unit) =
        launchCapture(DEFAULT_SIZE, onPhoto, onError)

    /** The same camera, at [READING_SIZE]; it waits its turn with [capturePhoto]. */
    val forReading: PhotoSource = object : PhotoSource {
        override fun capturePhoto(onPhoto: (Bitmap) -> Unit, onError: (String) -> Unit) =
            launchCapture(READING_SIZE, onPhoto, onError)
    }

    private fun launchCapture(size: Size, onPhoto: (Bitmap) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            captureLock.withLock {
                try {
                    capture(size, onPhoto, onError)
                } catch (e: ImageCaptureException) {
                    Log.e(TAG, "Phone camera could not take the picture", e)
                    onError(SpokenPhrases.PHONE_PHOTO_FAILED)
                } catch (e: Exception) {
                    Log.e(TAG, "Phone camera failed", e)
                    onError(SpokenPhrases.PHONE_CAMERA_UNAVAILABLE)
                }
            }
        }
    }

    private suspend fun capture(size: Size, onPhoto: (Bitmap) -> Unit, onError: (String) -> Unit) {
        if (ContextCompat.checkSelfPermission(activity, CAMERA) != PERMISSION_GRANTED) {
            return onError(SpokenPhrases.NO_PHONE_CAMERA_PERMISSION)
        }
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            return onError(SpokenPhrases.PHONE_CAMERA_NEEDS_SCREEN)
        }

        val provider = ProcessCameraProvider.awaitInstance(activity)
        if (!provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
            return onError(SpokenPhrases.NO_PHONE_CAMERA)
        }

        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    )
                    .build()
            )
            // So "upright" is the way the phone is being held.
            .setTargetRotation(activity.display?.rotation ?: Surface.ROTATION_0)
            .build()

        try {
            provider.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, imageCapture)
            // A camera another app holds never opens; nor does one VISOR left
            // the screen in the middle of opening.
            val photo = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) { takePicture(imageCapture) }
                ?: return onError(SpokenPhrases.PHONE_CAMERA_UNAVAILABLE)
            if (isBlack(photo)) return onError(SpokenPhrases.PHONE_PHOTO_TOO_DARK)
            onPhoto(photo)
        } finally {
            provider.unbind(imageCapture)
        }
    }

    private suspend fun takePicture(imageCapture: ImageCapture): Bitmap =
        suspendCancellableCoroutine { continuation ->
            imageCapture.takePicture(decoder, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val result = runCatching { image.use(::upright) }
                    result.onSuccess(continuation::resume).onFailure(continuation::resumeWithException)
                }

                override fun onError(exception: ImageCaptureException) {
                    continuation.resumeWithException(exception)
                }
            })
        }

    /** The photo the way up it was taken: ML Kit and the server read it as given. */
    private fun upright(image: ImageProxy): Bitmap {
        val bitmap = image.toBitmap()
        val degrees = image.imageInfo.rotationDegrees
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun isBlack(photo: Bitmap): Boolean {
        val side = BlackPhoto.SAMPLE_SIDE
        val sample = Bitmap.createScaledBitmap(photo, side, side, true)
        val pixels = IntArray(side * side).also { sample.getPixels(it, 0, side, 0, 0, side, side) }
        if (sample !== photo) sample.recycle()
        return BlackPhoto.isBlack(pixels)
    }

    fun close() {
        scope.cancel()
        decoder.shutdown()
    }

    private companion object {
        const val TAG = "VisorPhoneCamera"
        const val CAPTURE_TIMEOUT_MS = 10_000L

        // Identifying objects, describing scenes, hazards: about 2.8 MP, quick
        // to send to the server, and well past what the glasses give them.
        val DEFAULT_SIZE = Size(1920, 1440)

        // Reading text: about 5 MP, for small print held at arm's length.
        val READING_SIZE = Size(2592, 1944)
    }
}
