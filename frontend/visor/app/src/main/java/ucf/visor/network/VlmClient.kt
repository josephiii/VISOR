package ucf.visor.network

import android.graphics.Bitmap
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class VlmClient(
    private val baseUrl: String = "http://54.173.104.183:8000",
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun ask(
        bitmap: Bitmap,
        prompt: String,
        onResult: (String) -> Unit,
        onError: (Exception) -> Unit,
    ) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart(
                "image", "photo.jpg",
                stream.toByteArray().toRequestBody("image/jpeg".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("$baseUrl/vlm/image_prompt")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e)

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val text = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        onError(IOException("HTTP ${it.code}: $text"))
                        return
                    }
                    onResult(parseAnswer(text))
                }
            }
        })
    }

    private fun parseAnswer(raw: String): String = try {
        if (raw.trim().startsWith("{")) {
            val json = JSONObject(raw)
            json.optString("answer", json.optString("response", raw))
        } else raw.trim().removeSurrounding("\"")
    } catch (e: Exception) { raw }
}