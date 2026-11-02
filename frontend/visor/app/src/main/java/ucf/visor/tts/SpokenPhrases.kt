package ucf.visor.tts

import ucf.visor.network.Hazard
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

    // A capture has started. With the phone's camera, VISOR says so every
    // time: it is the cue to hold the phone up, and nobody has to remember
    // which camera they chose.
    const val TAKING_PICTURE = "Taking picture"
    const val TAKING_PICTURE_WITH_PHONE = "Taking picture with your phone"
    const val AWAITING_SCENE_DESCRIPTION = "Awaiting scene description"

    // Glasses camera.
    const val NO_CAMERA_PERMISSION = "VISOR does not have camera access. Allow it in the Meta AI app."
    const val GLASSES_CAMERA_UNAVAILABLE = "I couldn't connect to your glasses camera."
    const val GLASSES_PHOTO_FAILED = "I couldn't take a picture with your glasses."
    const val GLASSES_PHOTO_UNREADABLE = "I couldn't read the photo from your glasses."

    /** No glasses to take the picture: what to check, and the way to carry on without them. */
    const val NO_GLASSES_TRY_PHONE =
        "No connected glasses were found. Make sure your glasses are on and connected, " +
            "or switch to your phone's camera on the Pair screen."

    // Phone camera.
    const val NO_PHONE_CAMERA_PERMISSION =
        "VISOR does not have access to your phone's camera. Allow it in your phone's settings."
    const val PHONE_CAMERA_NEEDS_SCREEN = "Open VISOR on your phone to use its camera."
    const val NO_PHONE_CAMERA = "I couldn't find a camera on the back of your phone."
    const val PHONE_CAMERA_UNAVAILABLE =
        "I couldn't use your phone's camera. Another app may be using it."
    const val PHONE_PHOTO_FAILED = "I couldn't take a picture with your phone."

    /** A black picture: most likely the phone is in a pocket, or face down. */
    const val PHONE_PHOTO_TOO_DARK =
        "The picture came out black. Make sure nothing is covering the camera on the back of your phone."

    /** Said when the user switches cameras (Pair screen or "VISOR GO, use my phone camera"). */
    const val USING_PHONE_CAMERA =
        "Using your phone's camera. Point the back of your phone at what you want to know about."
    const val USING_GLASSES_CAMERA = "Using your glasses' camera."

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

    /**
     * "chair, 3 feet ahead on your left. person, 6 feet ahead." Nearest first,
     * in whole feet, and at most [limit] of them.
     *
     * Which side each one is on comes from where it is in the picture,
     * [imageWidth] pixels across: the left third is on the wearer's left, the
     * right third on their right, and the middle is straight ahead. Without a
     * width (0), no side is said.
     */
    fun hazardsAhead(
        hazards: List<Hazard>,
        imageWidth: Int = 0,
        limit: Int = Int.MAX_VALUE,
    ): String =
        if (hazards.isEmpty()) NO_HAZARDS_FOUND
        else hazards.sortedBy { it.depth }.take(limit.coerceAtLeast(1)).joinToString(" ") { hazard ->
            val feet = (hazard.depth * FEET_PER_METER).roundToInt().coerceAtLeast(1)
            val side = when (sideOf(hazard, imageWidth)) {
                Side.LEFT -> " on your left"
                Side.RIGHT -> " on your right"
                Side.AHEAD, null -> ""
            }
            "${hazard.name}, $feet ${if (feet == 1) "foot" else "feet"} ahead$side."
        }

    /** How many hazards a Brief answer names: the nearest, as the rest can wait. */
    const val BRIEF_HAZARD_LIMIT = 3

    private enum class Side { LEFT, AHEAD, RIGHT }

    /** Which third of the picture [hazard]'s box is centered in, or null if that can't be told. */
    private fun sideOf(hazard: Hazard, imageWidth: Int): Side? {
        if (imageWidth <= 0 || hazard.coords.size < 4) return null
        val center = (hazard.coords[0] + hazard.coords[2]) / 2 / imageWidth
        return when {
            center < 1f / 3 -> Side.LEFT
            center > 2f / 3 -> Side.RIGHT
            else -> Side.AHEAD
        }
    }

    /**
     * The reply to "VISOR GO, start session" (or "end session"). VISOR used to
     * need a session started before its camera features worked; now they
     * always do, and this says so to anyone still in that habit.
     */
    const val NO_SESSION_NEEDED =
        "VISOR is always ready. Just say \"VISOR, read this\", or press a button on Home."
}
