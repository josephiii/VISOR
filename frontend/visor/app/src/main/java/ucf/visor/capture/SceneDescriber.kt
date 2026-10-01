package ucf.visor.capture

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import ucf.visor.network.VlmClient
import ucf.visor.ocr.ObjectDetector

class SceneDescriber(
    private val photoSource: PhotoSource,
    private val vlm: VlmClient,
    private val fallback: ObjectDetector? = null,
    private val prompt: String = DEFAULT_PROMPT,
) : ReadRequester {

    companion object {
        const val DEFAULT_PROMPT =
            "Describe this scene in two or three short sentences for a blind person. " +
                    "Mention the main objects and where they are relative to the viewer."

        private const val MAX_IMAGE_SIDE = 1024
    }

    private val main = Handler(Looper.getMainLooper())

    override fun requestRead(onText: (String) -> Unit) {
        // OkHttp calls back on a background thread, so hop to main before speaking
        val deliver: (String) -> Unit = { text -> main.post { onText(text) } }

        photoSource.capturePhoto(
            onPhoto = { bitmap -> describe(bitmap, deliver) },
            onError = { message -> deliver(message) },
        )
    }

    private fun describe(bitmap: Bitmap, deliver: (String) -> Unit) {
        vlm.ask(
            bitmap = downscale(bitmap, MAX_IMAGE_SIDE),
            prompt = prompt,
            onResult = { answer ->
                deliver(answer.ifBlank { "I couldn't describe that." })
            },
            onError = { e ->
                Log.e("VISOR", "Scene VLM request failed", e)
                describeOnDevice(bitmap, deliver)
            },
        )
    }

    private fun describeOnDevice(bitmap: Bitmap, deliver: (String) -> Unit) {
        if (fallback == null) {
            deliver("Sorry, I couldn't reach the server.")
            return
        }
        fallback.describe(
            bitmap = bitmap,
            onResult = deliver,
            onError = { deliver("Sorry, I couldn't tell what's there.") },
        )
    }

    private fun downscale(src: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxSide) return src
        val scale = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt(),
            (src.height * scale).toInt(),
            true,
        )
    }
}