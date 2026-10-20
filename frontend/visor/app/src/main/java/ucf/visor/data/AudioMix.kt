package ucf.visor.data

import kotlin.math.roundToInt

/**
 * One of the kinds of sound VISOR makes, each with its own volume slider in
 * Settings. They are split by what a sound is for, so a user can quiet what
 * they don't need (say, "Starting session", when they can feel the button
 * change) without losing what they do (the answers).
 */
sealed interface AudioCategory {
    /** Where its volume is saved. Fixed, so renaming a constant never loses a user's setting. */
    val key: String

    /** Its volume until the user moves its slider. */
    val defaultLevel: Float

    companion object {
        /** Every category, in the order Settings lists them. */
        val all: List<AudioCategory> get() = VoiceCategory.entries + SoundCategory.entries
    }
}

/** What VISOR's voice is saying. */
enum class VoiceCategory(override val key: String) : AudioCategory {
    /**
     * What VISOR reads, identifies, describes and finds ahead, and what went
     * wrong when it can't, including why a session could not start.
     */
    ANSWERS("answersVolume"),

    /** "Starting session", "Ending session", switching modes, and "Taking picture". */
    SESSION_UPDATES("sessionUpdatesVolume"),

    /**
     * VISOR's side of a voice conversation: "Listening." after "VISOR GO", its
     * replies to commands, and the spoken setup questions.
     */
    VOICE_COMMANDS("voiceCommandsVolume");

    override val defaultLevel: Float get() = Volumes.Max
}

/**
 * VISOR's sound effects. There are none for listening: the phone's speech
 * recognizer plays its own tones as it starts and stops listening, at the
 * phone's media volume, and VISOR's would only double them.
 */
enum class SoundCategory(override val key: String) : AudioCategory {
    /** While VISOR works on an answer. */
    THINKING_BEEPS("thinkingBeepsVolume"),

    /** As a session starts, and as it ends. */
    SESSION_CHIMES("sessionChimesVolume");

    override val defaultLevel: Float get() = Volumes.DefaultSoundEffects
}

/**
 * How loud each of VISOR's sounds is, like a mixer: [master] ("All sounds")
 * turns all of them up or down together, and each category's own level sets it
 * within that. With all sounds at 50% and answers at 80%, answers play at 40%
 * of the phone's media volume. At 0, a slider silences everything it controls.
 */
data class AudioMix(
    val master: Float = Volumes.Max,
    private val levels: Map<AudioCategory, Float> = AudioCategory.all.associateWith { it.defaultLevel },
) {
    /** [category]'s own slider. */
    fun level(category: AudioCategory): Float = levels[category] ?: category.defaultLevel

    /** How loud [category] actually plays, as a fraction of the media volume. */
    fun loudness(category: AudioCategory): Float = master * level(category)

    fun withMaster(level: Float): AudioMix = copy(master = level)

    fun with(category: AudioCategory, level: Float): AudioMix =
        copy(levels = levels + (category to level))
}

/**
 * The volume sliders' scale: a fraction of the phone's media volume, from off
 * to full, in ten-percent notches. All of VISOR's sounds play on the media
 * stream, so the phone's volume buttons still turn everything up or down; these
 * set VISOR's own sounds within that.
 */
object Volumes {

    /** Every slider's far left: silent. */
    const val Off = 0f

    const val Max = 1f

    /** The sliders' granularity: ten percent. */
    const val Step = 0.1f

    /** The thinking beep's loudness from before it was adjustable; the session chimes start there too. */
    const val DefaultSoundEffects = 0.6f

    /** Notches strictly between a slider's ends: Off, 10% … 100% is eleven positions, nine notches. */
    val SliderSteps = ((Max - Off) / Step).roundToInt() - 1

    /** Rounds [volume] onto the sliders' grid and clamps it to [Off]..[Max]. */
    fun snap(volume: Float): Float = ((volume / Step).roundToInt() * Step).coerceIn(Off, Max)

    fun percent(volume: Float): Int = (volume * 100).roundToInt()

    /** Rounds to silence: what a slider set to "Off" plays at. */
    fun isOff(volume: Float): Boolean = percent(volume) == 0

    /** Short on-screen value, e.g. "80%", or "Off". */
    fun label(volume: Float): String = if (isOff(volume)) "Off" else "${percent(volume)}%"

    /** What a screen reader announces, and the volume preview says, e.g. "80 percent". */
    fun spokenLabel(volume: Float): String = if (isOff(volume)) "off" else "${percent(volume)} percent"
}
