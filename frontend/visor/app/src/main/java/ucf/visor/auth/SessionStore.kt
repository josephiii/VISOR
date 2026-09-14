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

    private val prefs = context.getSharedPreferences("visor_session", Context.MODE_PRIVATE)

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_LOGGED_IN, false)

    fun setLoggedIn(loggedIn: Boolean) {
        prefs.edit().putBoolean(KEY_LOGGED_IN, loggedIn).apply()
    }

    private companion object {
        const val KEY_LOGGED_IN = "isLoggedIn"
    }
}
