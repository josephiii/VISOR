package ucf.visor.ui.profile

import android.content.Context

/**
 * Local, on-device profile storage.
 *
 * The profile lives on the phone.
 * Backend stores only username/email/password; secure sync is future work.
 * Uses SharedPreferences so we add ZERO new dependencies.
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
            .putFloat(KEY_SPEECH_RATE_MULTIPLIER, p.speechRate)
            .putString(KEY_VERBOSITY, p.verbosity.name)
            .putBoolean(KEY_HIGH_CONTRAST, p.appHighContrast)
            .putString(KEY_TEXT_SCALE, p.textScale.name)
            .putBoolean(KEY_VOICE_NAV, p.voiceNavigationEnabled)
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
            speechRate = speechRateOrDefault(defaults.speechRate),
            verbosity = enumOrDefault(KEY_VERBOSITY, defaults.verbosity),
            appHighContrast = prefs.getBoolean(KEY_HIGH_CONTRAST, defaults.appHighContrast),
            textScale = enumOrDefault(KEY_TEXT_SCALE, defaults.textScale),
            voiceNavigationEnabled = prefs.getBoolean(
                KEY_VOICE_NAV,
                defaults.voiceNavigationEnabled
            ),
        )
    }

    /** True once the user has completed (or skipped through) first-run setup. */
    fun hasProfile(): Boolean = prefs.contains(KEY_SEVERITY)

    fun clear() = prefs.edit().clear().apply()   // for logout / delete account

    /**
     * Reads the speech rate, migrating anyone who last saved under the old
     * three-value enum.
     *
     * The enum wrote its name ("SLOW") to [KEY_SPEECH_RATE]; the multiplier
     * writes a float to a separate key. They have to be separate keys —
     * SharedPreferences would throw ClassCastException if getFloat found the
     * old string sitting under the same name.
     */
    private fun speechRateOrDefault(default: Float): Float {
        val saved = if (prefs.contains(KEY_SPEECH_RATE_MULTIPLIER)) {
            prefs.getFloat(KEY_SPEECH_RATE_MULTIPLIER, default)
        } else {
            when (prefs.getString(KEY_SPEECH_RATE, null)) {
                "SLOW" -> SpeechRates.Slow
                "NORMAL" -> SpeechRates.Normal
                "FAST" -> SpeechRates.Fast
                else -> default
            }
        }
        // Clamped on the way in, not just on the way out: the ceiling has moved
        // once already, and a profile saved under a higher one would otherwise
        // keep feeding an out-of-range rate to the TTS engine while the slider
        // showed it pinned at the top.
        return saved.coerceIn(SpeechRates.Min, SpeechRates.Max)
    }

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
        // Legacy key: the pre-slider enum name. Read for migration, never written.
        const val KEY_SPEECH_RATE = "speechRate"
        const val KEY_SPEECH_RATE_MULTIPLIER = "speechRateMultiplier"
        const val KEY_VERBOSITY = "verbosity"
        const val KEY_HIGH_CONTRAST = "appHighContrast"
        const val KEY_TEXT_SCALE = "textScale"
        const val KEY_VOICE_NAV = "voiceNavigationEnabled"
    }
}