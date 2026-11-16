package ucf.visor.capture

import android.graphics.Bitmap
import ucf.visor.tts.SpokenPhrases

/**
 * The phone's camera, for VISOR's engine, which outlives any screen. The
 * camera itself ([PhoneCameraSource]) belongs to the screen showing VISOR,
 * which attaches it here while it exists. With no screen, as with the screen
 * off and the phone in a pocket, it says to open VISOR, as the camera does
 * itself while VISOR is not on screen.
 */
class PhoneCameraSlot : PhotoSource {

    private var camera: PhoneCameraSource? = null

    fun attach(source: PhoneCameraSource) {
        camera = source
    }

    /** Only [source] itself: a newer screen may already have attached its own. */
    fun detach(source: PhoneCameraSource) {
        if (camera === source) camera = null
    }

    override fun capturePhoto(onPhoto: (Bitmap) -> Unit, onError: (String) -> Unit) =
        camera?.capturePhoto(onPhoto, onError) ?: onError(SpokenPhrases.PHONE_CAMERA_NEEDS_SCREEN)

    /** The same camera for the hazard watch (no difference for the phone's camera). */
    val forWatch: PhotoSource get() = this

    /** The same camera at its reading size. */
    val forReading: PhotoSource = object : PhotoSource {
        override fun capturePhoto(onPhoto: (Bitmap) -> Unit, onError: (String) -> Unit) =
            camera?.forReading?.capturePhoto(onPhoto, onError)
                ?: onError(SpokenPhrases.PHONE_CAMERA_NEEDS_SCREEN)
    }
}
