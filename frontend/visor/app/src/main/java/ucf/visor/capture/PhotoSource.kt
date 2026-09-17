
package ucf.visor.capture

import android.graphics.Bitmap

// interface that delivers a bitmap when one is ready
interface PhotoSource {
    fun capturePhoto(onPhoto: (Bitmap) -> Unit, onError: (String) -> Unit)
}