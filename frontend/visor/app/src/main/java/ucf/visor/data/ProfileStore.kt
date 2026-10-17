package ucf.visor.data

import android.content.Context
import ucf.visor.stt.CameraCommand

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
        val editor = prefs.edit()
            .putString(KEY_USER_ID, p.userId)
            .putString(KEY_NAME, p.displayName)
            .putStringSet(KEY_VISION_TYPES, p.visionTypes.map { it.name }.toSet())
            .putString(KEY_DESCRIPTION, p.visionDescription)
            .putString(KEY_SEVERITY, p.severity.name)
            .putFloat(KEY_SPEECH_RATE_MULTIPLIER, p.speechRate)
            .putFloat(KEY_MASTER_VOLUME, p.audio.master)
            .putString(KEY_VERBOSITY, p.verbosity.name)
            .putBoolean(KEY_HIGH_CONTRAST, p.appHighContrast)
            .putString(KEY_TEXT_SCALE, p.textScale.name)
            .putBoolean(KEY_VOICE_NAV, p.voiceNavigationEnabled)
            .putBoolean(KEY_GLASSES_TAP_NAV, p.glassesTapNavigationEnabled)
            // The ones turned off, not the ones on: a camera command added in
            // a later version is then on for everyone, like every command.
            .putStringSet(
                KEY_CAMERA_COMMANDS_OFF,
                (CameraCommand.entries - p.cameraCommands).map { it.name }.toSet(),
            )
            .putBoolean(KEY_VOICE_LAUNCH, p.voiceLaunchEnabled)
        for (category in AudioCategory.all) editor.putFloat(category.key, p.audio.level(category))
        editor.apply()
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
            audio = audioMixOrDefault(defaults.audio),
            verbosity = enumOrDefault(KEY_VERBOSITY, defaults.verbosity),
            appHighContrast = prefs.getBoolean(KEY_HIGH_CONTRAST, defaults.appHighContrast),
            textScale = enumOrDefault(KEY_TEXT_SCALE, defaults.textScale),
            voiceNavigationEnabled = prefs.getBoolean(
                KEY_VOICE_NAV,
                defaults.voiceNavigationEnabled
            ),
            glassesTapNavigationEnabled = prefs.getBoolean(
                KEY_GLASSES_TAP_NAV,
                defaults.glassesTapNavigationEnabled
            ),
            cameraCommands = defaults.cameraCommands - prefs.getStringSet(KEY_CAMERA_COMMANDS_OFF, null)
                .orEmpty()
                .mapNotNull { runCatching { CameraCommand.valueOf(it) }.getOrNull() }
                .toSet(),
            voiceLaunchEnabled = prefs.getBoolean(KEY_VOICE_LAUNCH, defaults.voiceLaunchEnabled),
        )
    }

    /** True once the user has completed (or skipped through) first-run setup. */
    fun hasProfile(): Boolean = prefs.contains(KEY_SEVERITY)

    fun clear() = prefs.edit().clear().apply()   // for logout / delete account

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
        return saved.coerceIn(SpeechRates.Min, SpeechRates.Max)
    }

    /**
     * Before each kind of sound had its own slider there were two, one for
     * VISOR's voice and one for its beeps. Until the new ones are saved, every
     * voice category starts at the old voice volume and the thinking beeps at
     * the old beep volume, so updating VISOR makes nothing louder or quieter.
     */
    private fun audioMixOrDefault(default: AudioMix): AudioMix {
        val legacyVoice = floatOrNull(KEY_SPEECH_VOLUME)
        val legacyBeeps = floatOrNull(KEY_SOUND_EFFECTS_VOLUME)
        var mix = default.withMaster(floatOrNull(KEY_MASTER_VOLUME)?.asVolume() ?: default.master)
        for (category in AudioCategory.all) {
            val saved = floatOrNull(category.key) ?: when (category) {
                is VoiceCategory -> legacyVoice
                SoundCategory.THINKING_BEEPS -> legacyBeeps
                else -> null
            }
            if (saved != null) mix = mix.with(category, saved.asVolume())
        }
        return mix
    }

    private fun floatOrNull(key: String): Float? =
        if (prefs.contains(key)) prefs.getFloat(key, 0f) else null

    private fun Float.asVolume(): Float = coerceIn(Volumes.Off, Volumes.Max)

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

        // Legacy keys: the voice and beep volumes from before each kind of
        // sound had its own slider. Read for migration, never written.
        const val KEY_SPEECH_VOLUME = "speechVolume"
        const val KEY_SOUND_EFFECTS_VOLUME = "soundEffectsVolume"

        // "All sounds". Each kind of sound's own volume is saved under its AudioCategory.key.
        const val KEY_MASTER_VOLUME = "masterVolume"

        const val KEY_VERBOSITY = "verbosity"
        const val KEY_HIGH_CONTRAST = "appHighContrast"
        const val KEY_TEXT_SCALE = "textScale"
        const val KEY_VOICE_NAV = "voiceNavigationEnabled"
        const val KEY_GLASSES_TAP_NAV = "glassesTapNavigationEnabled"
        const val KEY_CAMERA_COMMANDS_OFF = "cameraCommandsOff"
        const val KEY_VOICE_LAUNCH = "voiceLaunchEnabled"
    }
}