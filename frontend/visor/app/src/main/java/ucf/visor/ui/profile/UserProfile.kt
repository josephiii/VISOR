package ucf.visor.ui.profile

/** How the user describes their vision. Multi-select — conditions can combine. */
enum class VisionType { CENTRAL_LOSS, PERIPHERAL_LOSS, BLUR_LOW_ACUITY, CONTRAST_LIGHT, NOT_SURE }

/** How much their vision affects daily life. */
enum class Severity { MILD, MODERATE, SEVERE }

/**
 * TTS speaking speed, shown as a 3-notch slider in Settings and onboarding.
 * [multiplier] is passed straight to `TextToSpeech.setSpeechRate()` — see
 * `tts.Speaker.setSpeechRate` — so this is the one enum in this file that
 * isn't just a label, it's the actual value the audio output uses.
 */
enum class SpeechRate(val multiplier: Float) { SLOW(0.75f), NORMAL(1.0f), FAST(1.35f) }

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
    val speechRate: SpeechRate = SpeechRate.NORMAL,
    val verbosity: Verbosity = Verbosity.STANDARD,
    val appHighContrast: Boolean = false,
    val textScale: TextScale = TextScale.EXTRA_LARGE,
    /** Say "VISOR GO" to navigate by voice. Defaults on, matching that TTS
     *  and the OCR wake-word listener are already always-on with no toggle. */
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

fun SpeechRate.label(): String = when (this) {
    SpeechRate.SLOW -> "Slower"
    SpeechRate.NORMAL -> "Normal"
    SpeechRate.FAST -> "Faster"
}
