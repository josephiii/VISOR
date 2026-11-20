package ucf.visor.lookaround

import ucf.visor.lookaround.ScanSequencer.Cue
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Everything Look around says, in one place, as SpokenPhrases is for the other
 * camera features. The wearer's eyes are no help here: they are turning their
 * head, often with the phone in a pocket. So every step is said, short, and
 * each outcome always sounds the same.
 *
 * Directions are clock hours (12 straight ahead, 9 on the left, 3 on the
 * right), as orientation and mobility training teaches them, and distances are
 * in feet, as Check for hazards says them.
 */
object LookAroundPhrases {

    private const val FEET_PER_METER = 3.28084

    fun cue(cue: Cue): String = when (cue) {
        Cue.HOLD_AHEAD -> "Looking around. Hold your head still."
        Cue.TURN_LEFT -> "Now turn your head slowly to the left."
        Cue.KEEP_LEFT -> "A little further left."
        Cue.TURN_RIGHT -> "Now turn slowly to the right."
        Cue.KEEP_RIGHT -> "A little further right."
        Cue.HOLD_STILL -> "Hold still."
        Cue.FACE_AHEAD -> "Now face ahead again."
    }

    // Before a scan can start.
    const val NEEDS_GLASSES =
        "Look around follows your head with your glasses' motion sensors. " +
            "Switch to your glasses' camera on the Pair screen."
    const val ALREADY_LOOKING = "I'm already looking around."

    // A scan that could not finish.
    const val LOST_HEAD_MOTION = "I lost track of your head movement, so I stopped looking around."
    const val NOT_CHECKED = "Sorry, I couldn't check what's around you."
    const val STOPPED = "Stopped looking around."

    /** The detector only knows everyday objects: an empty answer must not sound like the room is empty. */
    const val NOTHING_RECOGNIZED = "I didn't recognize any objects around you."

    /**
     * What was found, from the wearer's left to their right, the order they
     * just turned through, so the answer builds a picture of the room in the
     * same order. At most [limit], the nearest ones (a Brief answer), then any
     * side that went without a picture ([missedSides]: positive is left).
     */
    fun found(
        found: List<Sighting>,
        facingYawDeg: Double,
        limit: Int = Int.MAX_VALUE,
        missedSides: List<Double> = emptyList(),
        guideHint: Boolean = false,
    ): String {
        val missed = missed(missedSides)
        if (found.isEmpty()) return listOfNotNull(NOTHING_RECOGNIZED, missed).joinToString(" ")
        val said = found.sortedBy { it.distanceM }.take(limit.coerceAtLeast(1))
            .sortedByDescending { it.relativeTo(facingYawDeg) }
        val items = said.joinToString(" ") { sighting ->
            "${capitalized(sighting.name)} at ${hour(sighting, facingYawDeg)}, ${feet(sighting.distanceM)} away."
        }
        val hint = if (guideHint) "To find one, say VISOR GO, then guide me to the ${said.first().name}." else null
        return listOfNotNull("From left to right:", items, missed, hint).joinToString(" ")
    }

    /** "I couldn't get a picture of your left." for the sides that went without one. */
    internal fun missed(sides: List<Double>): String? {
        val left = sides.any { it > 0 }
        val right = sides.any { it < 0 }
        val where = when {
            left && right -> "your left or right"
            left -> "your left"
            right -> "your right"
            else -> return null
        }
        return "I couldn't get a picture of $where."
    }

    // Guiding the head to something found.

    /** Where [target] is, and which way to turn to face it. */
    fun guideStart(target: Sighting, facingYawDeg: Double): String {
        val relative = target.relativeTo(facingYawDeg)
        val name = capitalized(target.name)
        return when {
            abs(relative) <= GuideSequencer.ON_TARGET_DEG -> "$name, straight ahead, ${feet(target.distanceM)} away."
            relative > 0 -> "$name, at ${hour(target, facingYawDeg)}. Turn your head slowly to the left."
            else -> "$name, at ${hour(target, facingYawDeg)}. Turn your head slowly to the right."
        }
    }

    fun arrived(target: Sighting): String =
        "${capitalized(target.name)}, straight ahead, ${feet(target.distanceM)} away."

    /** Turned past it: back the other way. [toLeft] is which way to turn. */
    fun overshot(toLeft: Boolean): String = if (toLeft) "Too far. Back to the left." else "Too far. Back to the right."

    fun gaveUp(target: Sighting, facingYawDeg: Double): String =
        "I'll stop guiding. The ${target.name} is at ${hour(target, facingYawDeg)}."

    const val GUIDE_NEEDS_LOOK =
        "Look around first, so I know where things are. Say VISOR, look around."
    const val GUIDE_STOPPED = "Stopped guiding."
    const val GUIDE_WHAT = "What should I guide you to? Say VISOR GO, then guide me to, and its name."

    /** [query] wasn't among what was found; says what was, so the wearer can ask for one of those. */
    fun notFound(query: String, found: List<Sighting>): String {
        val asked = SceneMap.normalize(query).ifEmpty { query }
        val names = found.map { it.name }.distinct()
        val seen = when (names.size) {
            0 -> "I didn't recognize anything."
            1 -> "I found a ${names[0]}."
            else -> "I found " + names.dropLast(1).joinToString(", ") { "a $it" } + " and a ${names.last()}."
        }
        return "I didn't see a $asked when I looked around. $seen"
    }

    private fun hour(sighting: Sighting, facingYawDeg: Double): String =
        "${SceneMap.clockHour(sighting.relativeTo(facingYawDeg))} o'clock"

    private fun feet(meters: Float): String {
        val feet = (meters * FEET_PER_METER).roundToInt().coerceAtLeast(1)
        return "$feet ${if (feet == 1) "foot" else "feet"}"
    }

    private fun capitalized(name: String): String = name.replaceFirstChar { it.uppercase() }
}
