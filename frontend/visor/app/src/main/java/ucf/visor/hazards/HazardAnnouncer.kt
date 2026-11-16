package ucf.visor.hazards

import ucf.visor.network.Hazard
import ucf.visor.tts.SpokenPhrases

/** How far away a hazard is, closest first, so a smaller one is a closer one. */
enum class DistanceBucket { VERY_CLOSE, CLOSE, AHEAD }

/** Closer than this many feet is [DistanceBucket.VERY_CLOSE]. */
const val VERY_CLOSE_FEET = 3.0

/** Up to this many feet is [DistanceBucket.CLOSE]; further is [DistanceBucket.AHEAD]. */
const val CLOSE_FEET = 8.0

private const val FEET_PER_METER = 3.28084

/** [depthMeters] as the bucket hazard watch groups it in. */
fun distanceBucketOf(depthMeters: Float): DistanceBucket {
    val feet = depthMeters * FEET_PER_METER
    return when {
        feet < VERY_CLOSE_FEET -> DistanceBucket.VERY_CLOSE
        feet <= CLOSE_FEET -> DistanceBucket.CLOSE
        else -> DistanceBucket.AHEAD
    }
}

/**
 * Decides which of the hazards in a picture are worth saying, for hazard
 * watch, which looks every few seconds: the same chair is not announced every
 * time.
 *
 * A hazard is remembered by its name, side and [DistanceBucket]. It is said
 * when it is new, when it has come closer, when it has been there a while
 * ([repeatMs]), and, while very close, a little more often ([criticalRepeatMs]).
 * One that has not been seen for [forgetMs] is forgotten, so it is said again
 * if it comes back.
 *
 * Pure Kotlin. Time is passed in, never read, so tests control it.
 */
class HazardAnnouncer(
    private val repeatMs: Long = DEFAULT_REPEAT_MS,
    private val criticalRepeatMs: Long = DEFAULT_CRITICAL_REPEAT_MS,
    private val forgetMs: Long = DEFAULT_FORGET_MS,
) {

    private data class Key(val name: String, val side: SpokenPhrases.Side?, val bucket: DistanceBucket)

    private class Seen(var lastSeenMs: Long, var lastSpokenMs: Long)

    private val memory = mutableMapOf<Key, Seen>()

    /** Forgets everything, as when hazard watch is turned off. */
    fun reset() = memory.clear()

    /**
     * The hazards in [hazards] (a picture [imageWidth] pixels wide, taken at
     * [nowMs]) that are worth saying now, nearest first: empty when nothing is
     * new. Items with the same name, side and bucket become one (the nearest).
     */
    fun next(hazards: List<Hazard>, imageWidth: Int, nowMs: Long): List<Hazard> {
        memory.entries.removeAll { nowMs - it.value.lastSeenMs > forgetMs }

        val items = hazards
            .groupBy { Key(it.name, SpokenPhrases.sideOf(it, imageWidth), distanceBucketOf(it.depth)) }
            .map { (key, same) -> key to same.minBy { it.depth } }
            .sortedBy { it.second.depth }

        val toSpeak = mutableListOf<Hazard>()
        for ((key, hazard) in items) {
            val seen = memory[key]
            val speak = if (seen != null) {
                val sinceSpoken = nowMs - seen.lastSpokenMs
                sinceSpoken > repeatMs ||
                    (key.bucket == DistanceBucket.VERY_CLOSE && sinceSpoken > criticalRepeatMs)
            } else {
                // Same thing, other distance: said only when it came closer
                // than anything remembered. If it moved away it is not news.
                val others = memory.filterKeys { it.name == key.name && it.side == key.side }
                others.isEmpty() || key.bucket < others.keys.minOf { it.bucket }
            }
            if (speak) toSpeak += hazard

            val entry = seen ?: Seen(
                lastSeenMs = nowMs,
                // Moved away without being said: its last mention still counts.
                lastSpokenMs = if (speak) nowMs else memory.filterKeys { it.name == key.name && it.side == key.side }
                    .values.maxOf { it.lastSpokenMs },
            )
            entry.lastSeenMs = nowMs
            if (speak) entry.lastSpokenMs = nowMs
            memory[key] = entry
        }
        return toSpeak
    }

    companion object {
        const val DEFAULT_REPEAT_MS = 20_000L
        const val DEFAULT_CRITICAL_REPEAT_MS = 6_000L
        const val DEFAULT_FORGET_MS = 6_000L
    }
}
