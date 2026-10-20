package ucf.visor.data

import android.content.Context

/**
 * The logged-in session on this device: the tokens POST /auth/login returned
 * (sent as the bearer token on authenticated calls, e.g. deleting the
 * account and the VLM request) and whether someone is logged in, so a
 * returning user lands directly on Home (see VisorViewModel.startDestination)
 * instead of signing in every time the app opens.
 *
 * Not yet handled: refreshing an expired access token with [refreshToken], and
 * revoking tokens on logout (logout only forgets them on this device).
 */
class SessionStore(context: Context) {

    // TODO(security): these are auth tokens — move to EncryptedSharedPreferences
    //  (androidx.security:security-crypto) before shipping. Plain prefs for now.
    private val prefs = context.getSharedPreferences("visor_session", Context.MODE_PRIVATE)

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_LOGGED_IN, false)

    fun setLoggedIn(loggedIn: Boolean) {
        prefs.edit().putBoolean(KEY_LOGGED_IN, loggedIn).apply()
    }

    fun accessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun refreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    /** Persist the tokens returned by POST /auth/login. */
    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    /** Wipe all session data (logout / account deletion). */
    fun clear() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .putBoolean(KEY_LOGGED_IN, false)
            .apply()
    }

    private companion object {
        const val KEY_LOGGED_IN = "isLoggedIn"
        const val KEY_ACCESS_TOKEN = "accessToken"
        const val KEY_REFRESH_TOKEN = "refreshToken"
    }
}
