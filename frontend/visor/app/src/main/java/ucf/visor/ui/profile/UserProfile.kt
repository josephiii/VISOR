package ucf.visor.ui.profile

import java.util.Locale
import kotlin.math.roundToInt

/** How the user describes their vision. Multi-select — conditions can combine. */
enum class VisionType { CENTRAL_LOSS, PERIPHERAL_LOSS, BLUR_LOW_ACUITY, CONTRAST_LIGHT, NOT_SURE }

/** How much their vision affects daily life. */
enum class Severity { MILD, MODERATE, SEVERE }

/**
 * TTS speaking speed, as the multiplier handed straight to
 * `TextToSpeech.setSpeechRate()` — see `tts.Speaker.setSpeechRate`. This is
 * the one value in this file that isn't just a label; it is what the audio
 * output actually uses.
 *
 * This was a three-value enum (0.75 / 1.0 / 1.35). It is a plain multiplier
 * now because those three notches serve the middle of VISOR's audience and
 * nobody else: practised screen-reader users among the low-vision community
 * routinely listen far faster than any "Faster" preset, and users who are new
 * to synthetic speech often need to go slower than the slowest one.
 *
 * The three old values survive as [Slow], [Normal] and [Fast] — voice control
 * still moves between them, so "speak faster" stays one predictable jump
 * rather than a 0.25 nudge a user listening rather than looking cannot detect.
 */
object SpeechRates {

    /**
     * Roughly how many syllables a second English TTS produces at [Normal].
     * Conversational English runs about four to five syllables a second; the
     * upper figure is used so the ceiling below lands on a round multiplier.
     *
     * This is the conversion the slider's top end is defined in terms of, so
     * it is the number to change if the ceiling ever feels wrong.
     */
    const val BaselineSyllablesPerSecond = 5f

    /** Slowest offered. Below this, TTS engines start to distort badly. */
    const val Min = 0.5f

    /** The slider's granularity, and the nudge voice control falls back to. */
    const val Step = 0.25f

    /**
     * Fastest offered - about 15 syllables a second at [BaselineSyllablesPerSecond].
     *
     * The ceiling is a deliberate product decision rather than an engine limit:
     * Android TTS engines clamp the rate themselves, at different values, and
     * the clamp is not queryable. Holding the slider inside a range engines
     * honour keeps its top notches from silently doing nothing.
     */
    const val Max = 3.0f

    // The speeds VISOR shipped before the slider went continuous.
    const val Slow = 0.75f
    const val Normal = 1.0f
    const val Fast = 1.35f

    private val presets = listOf(Slow, Normal, Fast)

    /** Float comparison slack; every value here is a multiple of 0.05. */
    private const val Tolerance = 0.001f

    /** Notches strictly between the slider's two endpoints. */
    val SliderSteps = ((Max - Min) / Step).roundToInt() - 1

    /** Rounds [rate] onto the slider's grid and clamps it to the usable range. */
    fun snap(rate: Float): Float {
        val stepsFromMin = ((rate - Min) / Step).roundToInt()
        return (Min + stepsFromMin * Step).coerceIn(Min, Max)
    }

    /**
     * The next speed up from [from].
     *
     * Inside the old three-preset range this lands exactly on the next preset,
     * which is what makes "speak faster" an audible jump. Past the top preset
     * there is nothing left to jump to, so it falls back to one [Step] — that
     * is the half of the range only the slider used to be able to reach.
     */
    fun faster(from: Float): Float =
        presets.firstOrNull { it > from + Tolerance } ?: snap(from + Step)

    /**
     * The next speed down from [from].
     *
     * The presets sit at the bottom of the range, so above them this steps by
     * one [Step] like the slider does. Without that guard, a user listening at
     * 5x who asked to slow down a notch would be dropped all the way to [Fast]
     * in one word.
     */
    fun slower(from: Float): Float =
        if (from > Fast + Tolerance) snap(from - Step)
        else presets.lastOrNull { it < from - Tolerance } ?: snap(from - Step)

    /** [rate] expressed in the unit the ceiling is specified in. */
    fun syllablesPerSecond(rate: Float): Int = (rate * BaselineSyllablesPerSecond).roundToInt()

    /** Short on-screen label, e.g. "Faster · 1.25x". */
    fun label(rate: Float): String = "${band(rate)} · ${number(rate)}x"

    /**
     * What a screen reader announces, and what voice control says back.
     *
     * Written to be spoken rather than read: "1.25 times", not "1.25x", which
     * TTS engines render as a literal letter x. It carries the syllable rate
     * as well, because a user who cannot read the slider still needs to know
     * where in the range they have landed.
     */
    fun spokenLabel(rate: Float): String {
        val speed = if (band(rate) == "Normal") "normal speed" else "${number(rate)} times normal speed"
        return "$speed, about ${syllablesPerSecond(rate)} syllables a second"
    }

    private fun band(rate: Float): String = when {
        rate < Normal - Tolerance -> "Slower"
        rate > Normal + Tolerance -> "Faster"
        else -> "Normal"
    }

    /** Trims the trailing zeros %.2f leaves behind, so 1.0 reads "1", not "1.00". */
    private fun number(rate: Float): String =
        String.format(Locale.US, "%.2f", rate).trimEnd('0').trimEnd('.')
}

/** How much detail VISOR gives when describing scenes. */
enum class Verbosity { BRIEF, STANDARD, DETAILED }

/**
 * User-controlled text size, layered on top of the app's already-enlarged
 * base type scale. Section 508 calls for text resizable up to 200% without
 * losing content or functionality; EXTRA_LARGE combines with a user's OS
 * font scale setting to get well past that.
 */
enum class TextScale(val multiplier: Float) { STANDARD(1.0f), LARGE(1.15f), EXTRA_LARGE(1.3f) }

/**
 * User profile data model.
 *
 * Every field has a default so onboarding NEVER blocks first use: a user can
 * skip all setup and the app still works. Account identity (email/password)
 * lives in Supabase auth on the backend; this profile only links via [userId].
 */
data class UserProfile(
    val userId: String = "", // Supabase user.id from /auth/register
    val displayName: String = "",
    val visionTypes: Set<VisionType> = setOf(VisionType.NOT_SURE),
    val visionDescription: String = "",
    val severity: Severity = Severity.MODERATE,
    val speechRate: Float = SpeechRates.Normal,
    val verbosity: Verbosity = Verbosity.STANDARD,
    val appHighContrast: Boolean = false,
    val textScale: TextScale = TextScale.EXTRA_LARGE,
    /**
     * Say "VISOR GO" to navigate by voice. Off until the user asks for it, from
     * the toggle on TitleScreen or the one in Settings — VISOR does not start
     * listening, or talking through onboarding, on someone's behalf.
     */
    val voiceNavigationEnabled: Boolean = false,
)

// Human-readable labels — shared by the onboarding wizard and Settings' "About
// you" section so both ever say exactly the same thing for each option.
fun VisionType.label(): String = when (this) {
    VisionType.CENTRAL_LOSS -> "Trouble seeing the center"
    VisionType.PERIPHERAL_LOSS -> "Trouble seeing the sides"
    VisionType.BLUR_LOW_ACUITY -> "Everything is blurry"
    VisionType.CONTRAST_LIGHT -> "Contrast / light sensitivity"
    VisionType.NOT_SURE -> "Not sure"
}

fun Severity.label(): String = when (this) {
    Severity.MILD -> "A little"
    Severity.MODERATE -> "A moderate amount"
    Severity.SEVERE -> "A lot"
}
