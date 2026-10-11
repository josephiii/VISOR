package ucf.visor.capture

import android.graphics.Bitmap

class NotifyingPhotoSource(
    private val inner: PhotoSource,
    private val onPhotoTaken: () -> Unit,
) : PhotoSource {
    override fun capturePhoto(onPhoto: (Bitmap) -> Unit, onError: (String) -> Unit) {
        inner.capturePhoto(
            onPhoto = { bitmap ->
                onPhotoTaken()
                onPhoto(bitmap)
            },
            onError = onError,
        )
    }
}