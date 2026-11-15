package ucf.visor.hazards

/** How urgent a hazard is, least first, so they compare. */
enum class Urgency { NOTICE, WARNING, CRITICAL }

/** Distance bands, farthest first, so a closer band compares greater. */
enum class Band { AHEAD, CLOSE, VERY_CLOSE }

/** Which third of the picture it is in, and where its alert tone leans ([pan]: -1 left, 1 right). */
enum class Side(val pan: Float) { LEFT(-1f), AHEAD(0f), RIGHT(1f) }

/** What the hazard watch makes of one confirmed [track] right now. */
data class Scored(val track: Track, val urgency: Urgency, val band: Band, val side: Side, val inPath: Boolean)

/**
 * Decides, for one object, whether it is in the walking path, how close it is,
 * whether it is coming toward the user, and so how urgent it is. Holds no state:
 * what was already said is AlertPolicy's.
 */
class HazardScorer(private val config: HazardWatchConfig = HazardWatchConfig()) {

    /**
     * [track] scored, or null when it is too far away to matter. [lastBand] is
     * the band last said for it: a closer band counts only once the object is
     * clearly past its edge.
     */
    fun score(track: Track, lastBand: Band? = null): Scored? {
        val depth = track.depth
        val box = track.box
        val growth = track.growthPerSec
        val approaching = track.approachMps > config.approachMps || growth > config.growthPerSec
        // Time to contact from box growth: about 1 / (share of its height it grows each second).
        val contactS = if (growth > 0f) 1f / growth else Float.POSITIVE_INFINITY
        val vehicleClosingIn = track.name in VEHICLES && approaching && contactS < config.criticalContactS

        // Past the bands, only a vehicle closing in fast still matters.
        val band = band(depth, lastBand) ?: if (vehicleClosingIn) Band.AHEAD else return null
        val inPath = kotlin.math.abs(box.centerX - 0.5f) <= config.pathWidth / 2 && box.bottom >= 0.5f
        val side = when {
            box.centerX < 1f / 3 -> Side.LEFT
            box.centerX > 2f / 3 -> Side.RIGHT
            else -> Side.AHEAD
        }
        val moves = track.name in VEHICLES || track.name == "person"

        // By band rather than raw depth, so urgency shares the band's hysteresis.
        val urgency = when {
            inPath && band == Band.VERY_CLOSE -> Urgency.CRITICAL
            // A drop (stairs, curb, hole) in the path is a fall: critical from farther out.
            inPath && track.name in DROPS && depth < config.dropCriticalM -> Urgency.CRITICAL
            vehicleClosingIn -> Urgency.CRITICAL
            inPath && band == Band.CLOSE -> Urgency.WARNING
            moves && approaching && depth < config.approachWarnM -> Urgency.WARNING
            else -> Urgency.NOTICE
        }
        return Scored(track, urgency, band, side, inPath)
    }

    private fun band(depth: Float, lastBand: Band?): Band? {
        val raw = when {
            depth < config.veryCloseM -> Band.VERY_CLOSE
            depth < config.closeM -> Band.CLOSE
            depth < config.aheadM -> Band.AHEAD
            else -> return null
        }
        if (lastBand == null || raw <= lastBand) return raw
        // Closer than last said: only past the edge by the hysteresis margin.
        val edge = if (raw == Band.VERY_CLOSE) config.veryCloseM else config.closeM
        return if (depth <= edge - config.bandHysteresisM) raw else lastBand
    }

    private companion object {
        val VEHICLES = setOf("car", "bus", "truck", "motorcycle", "bicycle", "train", "scooter")

        /** YOLOE's hazard words for a change in ground level (backend HAZARD_WORDS). */
        val DROPS = setOf("stairs", "curb", "open manhole", "hole in the ground")
    }
}
