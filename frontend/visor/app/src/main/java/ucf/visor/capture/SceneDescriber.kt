package ucf.visor.capture

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ucf.visor.network.ApiResult
import ucf.visor.network.VlmClient
import ucf.visor.recognition.ObjectDetector
import ucf.visor.tts.SpokenPhrases

// "VISOR, what do you see?": takes a photo and asks the backend's VLM to
// describe it, falling back to on-device labels when the server can't answer
class SceneDescriber(
    private val photoSource: PhotoSource,
    private val vlm: VlmClient,
    // Main-thread scope (MainActivity's lifecycleScope): answers are spoken from it.
    private val scope: CoroutineScope,
    private val fallback: ObjectDetector? = null,
    private val prompt: String = DEFAULT_PROMPT,
) : ReadRequester {

    companion object {
        const val DEFAULT_PROMPT =
            "Describe this scene in two or three short sentences for a blind person. " +
                    "Mention the main objects and where they are relative to the viewer."

        private const val MAX_IMAGE_SIDE = 1024
    }

    override fun requestRead(onText: (String) -> Unit) {
        photoSource.capturePhoto(
            onPhoto = { bitmap -> describe(bitmap, onText) },
            onError = { message -> onText(message) },
        )
    }

    private fun describe(bitmap: Bitmap, deliver: (String) -> Unit) {
        scope.launch {
            when (val result = vlm.ask(downscale(bitmap, MAX_IMAGE_SIDE), prompt)) {
                is ApiResult.Success -> deliver(result.body.ifBlank { SpokenPhrases.SCENE_NOT_DESCRIBED })
                is ApiResult.Error -> {
                    Log.e("VISOR", "Scene VLM request failed (${result.code}): ${result.message}")
                    describeOnDevice(bitmap, deliver)
                }
            }
        }
    }

    private fun describeOnDevice(bitmap: Bitmap, deliver: (String) -> Unit) {
        if (fallback == null) {
            deliver(SpokenPhrases.SERVER_UNREACHABLE)
            return
        }
        fallback.describe(
            bitmap = bitmap,
            onResult = deliver,
            onError = { deliver(SpokenPhrases.RECOGNITION_FAILED) },
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
