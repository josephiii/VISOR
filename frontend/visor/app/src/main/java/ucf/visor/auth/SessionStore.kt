package ucf.visor.auth

import android.content.Context

/**
 * Minimal on-device stand-in for a real backend session.
 *
 * There's no backend auth yet — logging in/out is currently frontend-only
 * (see the TODOs on SettingsScreen's onLogout/onDeleteAccount about revoking
 * tokens and calling /auth/deleteAccount once the real endpoints land). Until
 * then, this single flag is the only record of "is someone logged in on this
 * device", so a returning user can skip TitleScreen/LoginScreen and land
 * directly on Home (see VisorViewModel.startDestination) instead of having to
 * sign in every time the app opens. When real backend sessions arrive, this
 * is the one place that needs to change: swap [isLoggedIn] for a real token
 * check, and callers (VisorViewModel.login/home/logout) stay the same.
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
