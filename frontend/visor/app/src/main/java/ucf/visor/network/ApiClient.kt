package ucf.visor.network

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import ucf.visor.BuildConfig
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** Outcome of a call to the VISOR backend. */
sealed interface ApiResult {
    /** [body] is the raw response text. */
    data class Success(val body: String) : ApiResult {
        /** [body] as a JSON object, or an empty one when it is not an object. */
        val json: JSONObject get() = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
    }

    /** [code] is the HTTP status, or -1 when the server could not be reached. */
    data class Error(val code: Int, val message: String) : ApiResult
}

/**
 * The one place that knows where the VISOR backend is and how to talk to it:
 * every endpoint wrapper ([AuthApi], [VlmClient], [HazardClient]) shares this
 * client and its error handling.
 *
 * The address is `visor_api_base_url` from local.properties (see
 * app/build.gradle.kts), so switching between the hosted server and a backend
 * on your own computer needs no code change. From the Android emulator, your
 * computer's localhost is 10.0.2.2, not 127.0.0.1:
 *   visor_api_base_url=http://10.0.2.2:8000
 */
object ApiClient {

    val baseUrl: String = BuildConfig.VISOR_API_BASE_URL.trimEnd('/')

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        // Scene descriptions and hazard detection run models on the server and take a while.
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JPEG = "image/jpeg".toMediaType()

    /** A photo for the endpoints that take one, as JPEG, compressed off the main thread. */
    suspend fun jpeg(bitmap: Bitmap): RequestBody = withContext(Dispatchers.Default) {
        ByteArrayOutputStream()
            .also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            .toByteArray()
            .toRequestBody(JPEG)
    }

    /**
     * Sends a request to [path] under [baseUrl] off the main thread;
     * [configure] sets its method and body. [accessToken], when given, goes
     * out as a bearer token.
     */
    suspend fun send(
        path: String,
        accessToken: String? = null,
        configure: Request.Builder.() -> Request.Builder,
    ): ApiResult = withContext(Dispatchers.IO) {
        val built = Request.Builder()
            .url("$baseUrl$path")
            .apply { if (!accessToken.isNullOrBlank()) header("Authorization", "Bearer $accessToken") }
            .configure()
            .build()

        try {
            http.newCall(built).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    ApiResult.Success(raw)
                } else {
                    // FastAPI's HTTPException serializes as { "detail": "..." }.
                    val detail = runCatching { JSONObject(raw).optString("detail") }.getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: "Request failed (HTTP ${response.code})"
                    ApiResult.Error(response.code, detail)
                }
            }
        } catch (e: Exception) {
            ApiResult.Error(code = -1, message = e.message ?: "Network error")
        }
    }
}
