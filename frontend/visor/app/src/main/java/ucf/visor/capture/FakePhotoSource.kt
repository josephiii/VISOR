package ucf.visor.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log

class FakePhotoSource(private val context: Context) : PhotoSource {

    override fun capturePhoto(onPhoto: (Bitmap) -> Unit, onError: (String) -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                context.assets.open("photo_test/sample_pill_bottle.jpg").use { input ->
                    val bitmap = BitmapFactory.decodeStream(input)
                    if (bitmap != null) onPhoto(bitmap)
                    else onError("Test image could not be decoded.")
                }
            } catch (e: Exception) {
                Log.e("VISOR", "Fake photo load failed", e)
                onError("Test image not found in assets/test/.")
            }
        }, 1000)
    }
}