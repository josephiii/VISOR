package ucf.visor.lookaround

import ucf.visor.network.Hazard
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One object found while looking around, and where it is.
 *
 * [bearingDeg] is its direction in the scan's own frame: 0 is where the wearer
 * faced when they asked, left is positive. It stays put as the head turns;
 * where it is *for the wearer* is [relativeTo] wherever they face now.
 */
data class Sighting(
    /** What it is, as the hazard detector names it: "chair", "dining table". */
    val name: String,
    val bearingDeg: Double,
    /** How far away, in meters. */
    val distanceM: Float,
    val confidence: Float,
    /** How far from the middle of its photo it was, in degrees: nearer the middle is more trustworthy. */
    val offCentreDeg: Double,
    /** Which picture of the scan it was in. */
    val photo: Int,
) {
    /** Its direction from a head facing [facingYawDeg], left positive, in -180..180. */
    fun relativeTo(facingYawDeg: Double): Double = wrap(bearingDeg - facingYawDeg)

    companion object {
        fun wrap(deg: Double): Double {
            var d = deg % 360
            if (d > 180) d -= 360
            if (d <= -180) d += 360
            return d
        }
    }
}

/**
 * Turns Look around's pictures into one map of what is around the wearer.
 *
 * Each picture's objects come from the backend's hazard detection (YOLO for
 * what and where in the photo, a depth model for how far). Their direction is
 * the head's direction when that photo was taken plus their place in the photo
 * ([CameraOptics.bearingInImage]). Neighbouring pictures overlap, so one chair
 * can be in two of them: [merge] keeps it once.
 */
object SceneMap {

    /** Two sightings of the same kind of object in different photos this close together are one object. */
    const val SAME_OBJECT_DEG = 12.0

    /** ... and only if their distances agree within this fraction. */
    const val SAME_DISTANCE_FRACTION = 0.4

    /** Every object in one photo, [widthPx] wide, taken with the head at [cameraYawDeg]. */
    fun sightings(hazards: List<Hazard>, widthPx: Int, cameraYawDeg: Double, photo: Int): List<Sighting> =
        hazards.mapNotNull { hazard ->
            if (hazard.coords.size < 4 || widthPx <= 0) return@mapNotNull null
            val centreX = (hazard.coords[0] + hazard.coords[2]) / 2.0
            val inImage = CameraOptics.bearingInImage(centreX, widthPx)
            Sighting(
                name = hazard.name.trim().lowercase(),
                bearingDeg = Sighting.wrap(cameraYawDeg + inImage),
                distanceM = hazard.depth,
                confidence = hazard.conf,
                offCentreDeg = abs(inImage),
                photo = photo,
            )
        }

    /**
     * One sighting per object. Sightings in *different* photos of the same
     * kind of object, close in direction and distance, are the same object
     * seen twice where the photos overlap; the one nearer its photo's middle
     * is kept. Two in the *same* photo are always two objects: the detector
     * already told them apart.
     */
    fun merge(all: List<Sighting>): List<Sighting> {
        val kept = mutableListOf<Sighting>()
        for (candidate in all.sortedBy { it.offCentreDeg }) {
            val twin = kept.firstOrNull { existing ->
                existing.name == candidate.name &&
                    existing.photo != candidate.photo &&
                    abs(Sighting.wrap(existing.bearingDeg - candidate.bearingDeg)) <= SAME_OBJECT_DEG &&
                    closeInDistance(existing.distanceM, candidate.distanceM)
            }
            if (twin == null) kept += candidate
        }
        return kept
    }

    private fun closeInDistance(a: Float, b: Float): Boolean {
        val larger = maxOf(a, b)
        if (larger <= 0f) return true
        return abs(a - b) / larger <= SAME_DISTANCE_FRACTION
    }

    /**
     * The clock hour of a direction [relativeDeg] from where the head faces
     * (left positive): 12 is straight ahead, 9 is the left, 3 the right, each
     * hour 30°. The clock face is how orientation and mobility training
     * teaches directions, so many low-vision users already think in it.
     */
    fun clockHour(relativeDeg: Double): Int {
        val hours = (Sighting.wrap(relativeDeg) / 30).roundToInt()
        return ((12 - hours - 1).mod(12)) + 1
    }

    /**
     * The object [query] names ("the chair", "table", "a person"), nearest
     * first when there are several, or null when nothing found matches.
     */
    fun find(found: List<Sighting>, query: String): Sighting? {
        val wanted = normalize(query)
        if (wanted.isEmpty()) return null
        val names = (Synonyms[wanted] ?: emptyList()) + wanted
        return found
            .filter { sighting -> names.any { matches(sighting.name, it) } }
            .minByOrNull { it.distanceM }
    }

    private fun matches(name: String, wanted: String): Boolean =
        name == wanted || name.split(' ').contains(wanted) || wanted.split(' ').containsAll(name.split(' '))

    /** [query] without articles, possessives or punctuation, singular. */
    internal fun normalize(query: String): String {
        val words = query.lowercase()
            .replace(Regex("[^a-z ]"), " ")
            .split(' ')
            .filter { it.isNotBlank() && it !in FillerWords }
        return words.joinToString(" ") { singular(it) }
    }

    private fun singular(word: String): String = when {
        word == "people" -> "person"
        word.length > 3 && word.endsWith("ies") -> word.dropLast(3) + "y"
        word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
        else -> word
    }

    private val FillerWords = setOf("the", "a", "an", "my", "nearest", "closest", "that", "this", "to", "me", "please")

    /** What people call the hazard detector's (COCO) object names. */
    private val Synonyms: Map<String, List<String>> = mapOf(
        "table" to listOf("dining table"),
        "sofa" to listOf("couch"),
        "plant" to listOf("potted plant"),
        "television" to listOf("tv"),
        "tv" to listOf("tv"),
        "phone" to listOf("cell phone"),
        "someone" to listOf("person"),
        "somebody" to listOf("person"),
        "man" to listOf("person"),
        "woman" to listOf("person"),
        "seat" to listOf("chair", "couch", "bench"),
        "computer" to listOf("laptop"),
        "fridge" to listOf("refrigerator"),
        "bike" to listOf("bicycle"),
    )
}
