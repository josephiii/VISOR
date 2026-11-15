package ucf.visor.hazards

import ucf.visor.network.Hazard
import kotlin.math.max
import kotlin.math.min

/** A box as shares of the frame, from 0 to 1, so frames of any size compare. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX: Float get() = (left + right) / 2
    val height: Float get() = bottom - top
    private val area: Float get() = max(0f, right - left) * max(0f, bottom - top)

    fun iou(other: Box): Float {
        val inter = Box(max(left, other.left), max(top, other.top), min(right, other.right), min(bottom, other.bottom)).area
        val union = area + other.area - inter
        return if (union > 0f) inter / union else 0f
    }
}

/**
 * One object followed across frames. [id] is new each time the object appears,
 * so something that leaves and comes back is announced again.
 */
class Track internal constructor(val id: Int, val name: String, box: Box, depth: Float, timeMs: Long) {
    var box: Box = box
        private set

    /** Seen or missed, newest last, over the confirm window. */
    internal val hits = ArrayDeque<Boolean>()

    /** The last few sightings, newest last, for smoothing and approach. */
    private val samples = ArrayDeque<Sample>()

    var lastSeenMs: Long = timeMs
        private set

    init {
        seen(box, depth, timeMs)
    }

    /** The median of the last few distances: one wild depth reading doesn't move it. */
    val depth: Float get() = samples.map { it.depth }.sorted().let { it[it.size / 2] }

    /** How fast it is getting closer, in m/s (negative when moving away). */
    val approachMps: Float get() = rate { old, new, seconds -> (old.depth - new.depth) / seconds }

    /** How fast its box grows, as a share of its height per second. Needs no depth model. */
    val growthPerSec: Float
        get() = rate { old, new, seconds -> if (old.height > 0f) (new.height / old.height - 1f) / seconds else 0f }

    fun isConfirmed(config: HazardWatchConfig): Boolean = hits.count { it } >= config.confirmHits

    internal fun seen(box: Box, depth: Float, timeMs: Long) {
        this.box = box
        lastSeenMs = timeMs
        samples.addLast(Sample(timeMs, depth, box.height))
        if (samples.size > SAMPLES) samples.removeFirst()
    }

    private inline fun rate(change: (Sample, Sample, Float) -> Float): Float {
        val old = samples.first()
        val new = samples.last()
        val seconds = (new.timeMs - old.timeMs) / 1000f
        return if (seconds >= MIN_RATE_SECONDS) change(old, new, seconds) else 0f
    }

    private data class Sample(val timeMs: Long, val depth: Float, val height: Float)

    private companion object {
        const val SAMPLES = 3
        const val MIN_RATE_SECONDS = 0.3f
    }
}

/**
 * Matches each frame's detections to the objects already being followed, so
 * the same chair is the same [Track] from one frame to the next. Pure Kotlin:
 * HazardTrackerTest runs it on scripted frames.
 */
class HazardTracker(private val config: HazardWatchConfig = HazardWatchConfig()) {
    private val tracks = mutableListOf<Track>()
    private var nextId = 1

    /**
     * Adds one frame's detections, from a picture [imageWidth] x [imageHeight]
     * pixels taken at [timeMs], and returns every object still being followed.
     */
    fun update(hazards: List<Hazard>, imageWidth: Int, imageHeight: Int, timeMs: Long): List<Track> {
        val unmatched = tracks.toMutableSet()
        for (hazard in hazards.sortedByDescending { it.conf }) {
            val box = hazard.box(imageWidth, imageHeight) ?: continue
            val match = unmatched
                .filter { it.name == hazard.name }
                .maxByOrNull { it.box.iou(box) }
                ?.takeIf { it.box.iou(box) >= config.matchIou }
            if (match != null) {
                unmatched -= match
                match.seen(box, hazard.depth, timeMs)
                match.hit(true)
            } else {
                tracks += Track(nextId++, hazard.name, box, hazard.depth, timeMs).also { it.hit(true) }
            }
        }
        unmatched.forEach { it.hit(false) }
        tracks.removeAll { timeMs - it.lastSeenMs > config.dropAfterMs }
        return tracks.toList()
    }

    /** Forgets everything, as when the watch stops. */
    fun clear() {
        tracks.clear()
    }

    private fun Track.hit(seen: Boolean) {
        hits.addLast(seen)
        if (hits.size > config.confirmWindow) hits.removeFirst()
    }

    private fun Hazard.box(width: Int, height: Int): Box? =
        if (width <= 0 || height <= 0 || coords.size < 4) null
        else Box(coords[0] / width, coords[1] / height, coords[2] / width, coords[3] / height)
}
