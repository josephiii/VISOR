package ucf.visor.capture

import android.graphics.Bitmap
import ucf.visor.data.CameraChoice

/**
 * The camera the user has chosen ([choice]), asked again for every picture, so
 * switching on the Pair screen or by voice takes effect on the very next one.
 */
class ChosenPhotoSource(
    private val glasses: PhotoSource,
    private val phone: PhotoSource,
    private val choice: () -> CameraChoice,
) : PhotoSource {
    override fun capturePhoto(onPhoto: (Bitmap) -> Unit, onError: (String) -> Unit) =
        when (choice()) {
            CameraChoice.GLASSES -> glasses
            CameraChoice.PHONE -> phone
        }.capturePhoto(onPhoto, onError)
}
