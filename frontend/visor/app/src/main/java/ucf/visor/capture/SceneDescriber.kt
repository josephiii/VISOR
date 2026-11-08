package ucf.visor.capture

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ucf.visor.data.Verbosity
import ucf.visor.network.ApiResult
import ucf.visor.network.VlmClient
import ucf.visor.recognition.ObjectDetector
import ucf.visor.tts.SpokenPhrases

// "VISOR, what do you see?": takes a photo and asks the backend's VLM to
// describe it, falling back to on-device labels when the server can't answer.
// ask() answers a spoken question about a fresh photo instead. How much either
// says follows the user's Detail setting (see ScenePrompts).
class SceneDescriber(
    private val photoSource: PhotoSource,
    private val vlm: VlmClient,
    // Main-thread scope (MainActivity's lifecycleScope): answers are spoken from it.
    private val scope: CoroutineScope,
    private val fallback: ObjectDetector? = null,
    // Read at each capture, so a change in Settings applies to the very next one.
    private val verbosity: () -> Verbosity = { Verbosity.STANDARD },
    // Read at each capture. Offline, a description goes straight to the
    // on-device labels rather than waiting for a server it can't reach.
    private val isOnline: () -> Boolean = { true },
) : ReadRequester {

    companion object {
        // Move these into SpokenPhrases if you want them with your other phrases
        private const val QUESTION_NO_ANSWER = "I couldn't work that out."
        private const val QUESTION_SERVER_DOWN = "Sorry, I couldn't reach the server to answer that."

        private const val MAX_IMAGE_SIDE = 1024
    }

    override fun requestRead(onText: (String) -> Unit) =
        run(ScenePrompts.describe(verbosity()), isQuestion = false, onText = onText)

    /** Takes a picture and answers a spoken question about it. */
    fun ask(question: String, onText: (String) -> Unit) =
        run(ScenePrompts.question(question, verbosity()), isQuestion = true, onText = onText)

    private fun run(promptToUse: String, isQuestion: Boolean, onText: (String) -> Unit) {
        photoSource.capturePhoto(
            onPhoto = { bitmap -> describe(bitmap, promptToUse, isQuestion, onText) },
            onError = { message -> onText(message) },
        )
    }

    private fun describe(
        bitmap: Bitmap,
        promptToUse: String,
        isQuestion: Boolean,
        deliver: (String) -> Unit,
    ) {
        scope.launch {
            if (!isOnline()) {
                // Said first, so labels aren't mistaken for a description.
                if (isQuestion) deliver(QUESTION_SERVER_DOWN)
                else describeOnDevice(bitmap) { deliver(SpokenPhrases.offlineScene(it)) }
                return@launch
            }
            when (val result = vlm.ask(downscale(bitmap, MAX_IMAGE_SIDE), promptToUse)) {
                is ApiResult.Success -> deliver(
                    result.body.ifBlank {
                        if (isQuestion) QUESTION_NO_ANSWER else SpokenPhrases.SCENE_NOT_DESCRIBED
                    }
                )
                is ApiResult.Error -> {
                    Log.e("VISOR", "Scene VLM request failed (${result.code}): ${result.message}")
                    // Labels can't answer a question, so only fall back for plain descriptions
                    if (isQuestion) deliver(QUESTION_SERVER_DOWN)
                    else describeOnDevice(bitmap, deliver)
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