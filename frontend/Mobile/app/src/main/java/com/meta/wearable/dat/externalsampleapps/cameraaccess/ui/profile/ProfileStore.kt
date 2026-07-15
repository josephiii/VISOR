package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui.profile

import android.content.Context

/**
 * VISOR-124 — local, on-device profile storage.
 *
 * MVP decision (per team discussion 7/9): the profile lives on the phone.
 * Backend stores only username/email/password; secure sync is future work
 * (VISOR-125, Joseph). Uses SharedPreferences so we add ZERO new dependencies.
 *
 * Usage:
 *   val store = ProfileStore(context)
 *   store.save(profile)          // call from ProfileCreationScreen's onFinished
 *   val profile = store.load()   // returns defaults if nothing saved yet
 */
class ProfileStore(context: Context) {

    private val prefs = context.getSharedPreferences("visor_profile", Context.MODE_PRIVATE)

    fun save(p: UserProfile) {
        prefs.edit()
            .putString(KEY_USER_ID, p.userId)
            .putString(KEY_NAME, p.displayName)
            .putStringSet(KEY_VISION_TYPES, p.visionTypes.map { it.name }.toSet())
            .putString(KEY_DESCRIPTION, p.visionDescription)
            .putString(KEY_SEVERITY, p.severity.name)
            .putString(KEY_SPEECH_RATE, p.speechRate.name)
            .putString(KEY_VERBOSITY, p.verbosity.name)
            .putBoolean(KEY_HIGH_CONTRAST, p.appHighContrast)
            .apply()
    }

    fun load(): UserProfile {
        val defaults = UserProfile()
        return UserProfile(
            userId = prefs.getString(KEY_USER_ID, defaults.userId) ?: defaults.userId,
            displayName = prefs.getString(KEY_NAME, defaults.displayName) ?: defaults.displayName,
            visionTypes = prefs.getStringSet(KEY_VISION_TYPES, null)
                ?.mapNotNull { runCatching { VisionType.valueOf(it) }.getOrNull() }
                ?.toSet()
                ?.ifEmpty { defaults.visionTypes }
                ?: defaults.visionTypes,
            visionDescription = prefs.getString(KEY_DESCRIPTION, defaults.visionDescription)
                ?: defaults.visionDescription,
            severity = enumOrDefault(KEY_SEVERITY, defaults.severity),
            speechRate = enumOrDefault(KEY_SPEECH_RATE, defaults.speechRate),
            verbosity = enumOrDefault(KEY_VERBOSITY, defaults.verbosity),
            appHighContrast = prefs.getBoolean(KEY_HIGH_CONTRAST, defaults.appHighContrast),
        )
    }

    /** True once the user has completed (or skipped through) first-run setup. */
    fun hasProfile(): Boolean = prefs.contains(KEY_SEVERITY)

    fun clear() = prefs.edit().clear().apply()   // for logout / delete account

    private inline fun <reified T : Enum<T>> enumOrDefault(key: String, default: T): T {
        val raw = prefs.getString(key, null) ?: return default
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }

    private companion object {
        const val KEY_USER_ID = "userId"
        const val KEY_NAME = "displayName"
        const val KEY_VISION_TYPES = "visionTypes"
        const val KEY_DESCRIPTION = "visionDescription"
        const val KEY_SEVERITY = "severity"
        const val KEY_SPEECH_RATE = "speechRate"
        const val KEY_VERBOSITY = "verbosity"
        const val KEY_HIGH_CONTRAST = "appHighContrast"
    }
}
