package ucf.visor.network

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream

/**
 * The backend's vision language model endpoint:
 *   POST /vlm/image_prompt  multipart { prompt, image (JPEG) } -> the answer text
 *
 * [accessToken] is sent with every request, so the server can start requiring
 * a login without an app change.
 */
class VlmClient(
    private val accessToken: () -> String? = { null },
) {
    /** On success, [ApiResult.Success.body] is the model's answer as plain text. */
    suspend fun ask(bitmap: Bitmap, prompt: String): ApiResult {
        val jpeg = withContext(Dispatchers.Default) {
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                .toByteArray()
        }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("image", "photo.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
            .build()

        return when (val result = ApiClient.send("/vlm/image_prompt", accessToken()) { post(body) }) {
            is ApiResult.Success -> ApiResult.Success(parseAnswer(result.body))
            is ApiResult.Error -> result
        }
    }

    // FastAPI sends a plain string answer as a JSON string ("..."), escapes and
    // all; accept an { "answer" } / { "response" } object too.
    private fun parseAnswer(raw: String): String =
        when (val value = runCatching { JSONTokener(raw).nextValue() }.getOrNull()) {
            is String -> value
            is JSONObject -> value.optString("answer", value.optString("response", raw))
            else -> raw.trim()
        }
}
