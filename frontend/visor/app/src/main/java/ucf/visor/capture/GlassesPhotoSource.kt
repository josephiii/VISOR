package ucf.visor.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.meta.wearable.dat.camera.Stream
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.types.PhotoData
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout


class GlassesPhotoSource(
    private val requestPermission: suspend (Permission) -> PermissionStatus,
    private val onCapturing: () -> Unit = {},
) : PhotoSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val startLock = Mutex()
    private var session: DeviceSession? = null
    private var stream: Stream? = null

    override fun capturePhoto(onPhoto: (Bitmap) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                val activeStream = ensureStream()
                onCapturing()
                activeStream.capturePhoto()
                    .onSuccess { data ->
                        val bitmap = toBitmap(data)
                        if (bitmap != null) onPhoto(bitmap)
                        else onError("I couldn't read the photo from your glasses.")
                    }
                    .onFailure { error, _ ->
                        Log.e("VISOR", "capturePhoto failed: $error")
                        resetStream()
                        onError("I couldn't take a picture with your glasses.")
                    }
            } catch (e: Exception) {
                Log.e("VISOR", "Glasses camera failed", e)
                resetStream()
                onError("I couldn't connect to your glasses camera.")
            }
        }
    }


    private suspend fun ensureStream(): Stream = startLock.withLock {
        stream?.let { return@withLock it }

        if (Wearables.checkPermissionStatus(Permission.CAMERA) != PermissionStatus.Granted) {
            check(requestPermission(Permission.CAMERA) == PermissionStatus.Granted) {
                "Camera permission denied"
            }
        }

        val newSession = Wearables.createSession(AutoDeviceSelector()).getOrThrow()
        newSession.start()
        withTimeout(15_000) { newSession.state.first { it == DeviceSessionState.STARTED } }

        val camera = newSession
            .addCamera(StreamConfiguration(videoQuality = VideoQuality.MEDIUM, frameRate = 15))
            .getOrThrow()
        val newStream = camera.stream

        // If the glasses stop the stream (hinges closed, taken off), drop our cached copy
        scope.launch {
            newStream.state.collect { state ->
                if (state == StreamState.STOPPED || state == StreamState.CLOSED) resetStream()
            }
        }

        newStream.start()
        withTimeout(15_000) { newStream.state.first { it == StreamState.STREAMING } }

        session = newSession
        stream = newStream
        newStream
    }

    private fun resetStream() {
        stream = null
        session?.stop()
        session = null
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
        resetStream()
        scope.cancel()
    }
}