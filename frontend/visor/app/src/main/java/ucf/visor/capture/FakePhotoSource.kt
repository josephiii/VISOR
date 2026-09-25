package ucf.visor.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log

class FakePhotoSource(
    private val context: Context,
    private val assetPath: String = "photo_test/sample_pill_bottle.jpg",
) : PhotoSource {

    override fun capturePhoto(onPhoto: (Bitmap) -> Unit, onError: (String) -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                context.assets.open(assetPath).use { input ->      // <- uses the path now
                    val bitmap = BitmapFactory.decodeStream(input)
                    if (bitmap != null) onPhoto(bitmap)
                    else onError("Test image could not be decoded.")
                }
            } catch (e: Exception) {
                Log.e("VISOR", "Fake photo load failed", e)
                onError("Test image not found at $assetPath.")
            }
        }, 1000)
    }
}