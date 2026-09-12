package ucf.visor.ui.profile

/**
 * VISOR-123 — User profile data model.
 *
 * Every field has a default so onboarding NEVER blocks first use: a user can
 * skip all setup and the app still works. Account identity (email/password)
 * lives in Supabase auth on the backend; this profile only links via [userId].
 */

/** How the user describes their vision. Multi-select — conditions can combine. */
enum class VisionType { CENTRAL_LOSS, PERIPHERAL_LOSS, BLUR_LOW_ACUITY, CONTRAST_LIGHT, NOT_SURE }

/** How much their vision affects daily life. */
enum class Severity { MILD, MODERATE, SEVERE }

/** TTS speaking speed. Maps to a notched slider in Settings later. */
enum class SpeechRate { SLOW, NORMAL, FAST }

/** How much detail VISOR gives when describing scenes. */
enum class Verbosity { BRIEF, STANDARD, DETAILED }

/**
 * User-controlled text size, layered on top of the app's already-enlarged
 * base type scale. Section 508 calls for text resizable up to 200% without
 * losing content or functionality; EXTRA_LARGE combines with a user's OS
 * font scale setting to get well past that.
 */
enum class TextScale(val multiplier: Float) { STANDARD(1.0f), LARGE(1.15f), EXTRA_LARGE(1.3f) }

data class UserProfile(
    val userId: String = "", // Supabase user.id from /auth/register
    val displayName: String = "",
    val visionTypes: Set<VisionType> = setOf(VisionType.NOT_SURE),
    /** Free-text "describe your vision in your own words" — patients don't fit fixed boxes.
     *  Added after the 7/8 ophthalmologist meeting. Spoken or typed. */
    val visionDescription: String = "",
    val severity: Severity = Severity.MODERATE,
    val speechRate: SpeechRate = SpeechRate.NORMAL,
    val verbosity: Verbosity = Verbosity.STANDARD,
    val appHighContrast: Boolean = true,
    val textScale: TextScale = TextScale.EXTRA_LARGE,
)
