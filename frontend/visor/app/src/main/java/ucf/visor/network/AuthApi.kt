package ucf.visor.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * The backend's account endpoints (auth_router, under "/auth"):
 *   POST   /auth/register       { username, email, password } -> { user_id }
 *   POST   /auth/login          { email, password }           -> { access_token, refresh_token }
 *   DELETE /auth/deleteAccount  bearer access token           -> { message }
 *
 * Call from any coroutine (rememberCoroutineScope, viewModelScope, ...); the
 * network work runs off the main thread in [ApiClient].
 */
object AuthApi {

    private val JSON = "application/json".toMediaType()

    suspend fun register(username: String, email: String, password: String): ApiResult =
        post(
            path = "/auth/register",
            payload = JSONObject()
                .put("username", username)
                .put("email", email)
                .put("password", password)
        )

    suspend fun login(email: String, password: String): ApiResult =
        post(
            path = "/auth/login",
            payload = JSONObject()
                .put("email", email)
                .put("password", password)
        )

    suspend fun deleteAccount(accessToken: String): ApiResult =
        ApiClient.send("/auth/deleteAccount", accessToken) { delete() }

    private suspend fun post(path: String, payload: JSONObject): ApiResult =
        ApiClient.send(path) { post(payload.toString().toRequestBody(JSON)) }
}
