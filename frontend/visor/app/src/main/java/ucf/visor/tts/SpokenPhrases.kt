package ucf.visor.tts

import ucf.visor.network.Hazard
import ucf.visor.ui.viewmodel.SessionMode
import kotlin.math.roundToInt

/**
 * What the camera features (read text, identify object, describe scene, check
 * for hazards) say aloud, in one place.
 *
 * VISOR's users hear these rather than read them, often without looking at the
 * phone, so each outcome should always sound the same: what happened, and what
 * to do next when there is something to do. Glasses session errors have their
 * own sentences in `ucf.visor.wearables.describeSessionError`.
 */
object SpokenPhrases {

    // A capture has started.
    const val TAKING_PICTURE = "Taking picture"
    const val AWAITING_SCENE_DESCRIPTION = "Awaiting scene description"

    // Glasses camera.
    const val NO_CAMERA_PERMISSION = "VISOR does not have camera access. Allow it in the Meta AI app."
    const val GLASSES_CAMERA_UNAVAILABLE = "I couldn't connect to your glasses camera."
    const val GLASSES_PHOTO_FAILED = "I couldn't take a picture with your glasses."
    const val GLASSES_PHOTO_UNREADABLE = "I couldn't read the photo from your glasses."

    // Reading text.
    const val NO_TEXT_FOUND = "No text found. Try moving closer."
    const val TEXT_UNREADABLE = "Sorry, I couldn't read that."

    // Identifying objects and describing scenes.
    const val NOTHING_RECOGNIZED = "I'm not sure what I'm looking at."
    const val RECOGNITION_FAILED = "Sorry, I couldn't tell what's there."
    const val SCENE_NOT_DESCRIBED = "I couldn't describe that."
    const val SERVER_UNREACHABLE = "Sorry, I couldn't reach the server."

    // Checking for hazards. The detector only knows everyday objects, so an
    // empty answer must not sound like the way is clear.
    const val NO_HAZARDS_FOUND = "I didn't recognize any objects ahead."
    const val HAZARDS_NOT_CHECKED = "Sorry, I couldn't check for hazards."

    private const val FEET_PER_METER = 3.28084

    /** "I see a cup, a table." from on-device labels, most confident first. */
    fun objectsSeen(labels: List<String>): String =
        if (labels.isEmpty()) NOTHING_RECOGNIZED
        else "I see " + labels.joinToString(", ") + "."

    /** "chair, 3 feet ahead. person, 6 feet ahead." Nearest first, in whole feet. */
    fun hazardsAhead(hazards: List<Hazard>): String =
        if (hazards.isEmpty()) NO_HAZARDS_FOUND
        else hazards.sortedBy { it.depth }.joinToString(" ") { hazard ->
            val feet = (hazard.depth * FEET_PER_METER).roundToInt().coerceAtLeast(1)
            "${hazard.name}, $feet ${if (feet == 1) "foot" else "feet"} ahead."
        }

    /** Said when the session switches to [mode], from Home, by voice, or in Phase 1. */
    fun modeActivated(mode: SessionMode): String = when (mode) {
        SessionMode.HAZARD -> "Hazard Awareness Mode Activated!"
        SessionMode.SCENE -> "Scene Description Mode Activated!"
        SessionMode.READER -> "Reading Assistance Mode Activated!"
    }
}
