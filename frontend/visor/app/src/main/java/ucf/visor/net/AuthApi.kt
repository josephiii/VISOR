package ucf.visor.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Thin client for the backend auth endpoints.
 *
 * The FastAPI routes live under auth_router's "/auth" prefix:
 *   POST /auth/register  { username, email, password } -> { user_id }
 *   POST /auth/login     { email, password }           -> { access_token, refresh_token }
 *
 * All work happens off the main thread, so callers just `AuthApi.login(...)`
 * from any coroutine (a composable's rememberCoroutineScope, viewModelScope, ...).
 */
object AuthApi {
    // The Android emulator reaches the host machine's localhost via 10.0.2.2,
    // NOT 127.0.0.1 (that address is the emulator itself). For a physical
    // device use the host's LAN IP, and for production a real https:// host.
    private const val BASE_URL = "http://10.0.2.2:8000"

    private val JSON = "application/json".toMediaType()
    private val client = OkHttpClient()

    /** Outcome of an auth request. [Success.body] is the parsed JSON response. */
    sealed interface Result {
        data class Success(val body: JSONObject) : Result
        data class Error(val code: Int, val message: String) : Result
    }

    suspend fun register(username: String, email: String, password: String): Result =
        post(
            path = "/auth/register",
            payload = JSONObject()
                .put("username", username)
                .put("email", email)
                .put("password", password)
        )

    suspend fun login(email: String, password: String): Result =
        post(
            path = "/auth/login",
            payload = JSONObject()
                .put("email", email)
                .put("password", password)
        )

    private suspend fun post(path: String, payload: JSONObject): Result =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE_URL$path")
                .post(payload.toString().toRequestBody(JSON))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    val json = runCatching { JSONObject(raw) }.getOrNull()

                    if (response.isSuccessful && json != null) {
                        Result.Success(json)
                    } else {
                        // FastAPI's HTTPException serializes as { "detail": "..." }.
                        val detail = json?.optString("detail")?.takeIf { it.isNotBlank() }
                            ?: "Request failed (HTTP ${response.code})"
                        Result.Error(response.code, detail)
                    }
                }
            } catch (e: Exception) {
                Result.Error(code = -1, message = e.message ?: "Network error")
            }
        }
}
