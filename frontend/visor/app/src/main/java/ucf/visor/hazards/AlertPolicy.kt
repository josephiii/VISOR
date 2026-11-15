package ucf.visor.hazards

/**
 * One thing to say: [count] objects called [name], grouped because they share
 * a side, band and urgency ("Three people ahead, close."). The words are
 * SpokenPhrases.hazardAlert's.
 */
data class Alert(val urgency: Urgency, val name: String, val count: Int, val side: Side, val band: Band)

/**
 * What keeps the hazard watch from repeating itself. Each frame brings a new
 * set of objects, but an object is spoken about only when something about it
 * changes:
 *  - it is new (confirmed for the first time, or back after it was gone),
 *  - it moved into a closer band, or into the walking path,
 *  - it became more urgent,
 *  - it is a warning still in the path after a while, or a critical one still critical.
 * Moving away or back and forth on a band's edge says nothing.
 *
 * At most one alert at a time, most urgent and nearest first; what doesn't fit
 * isn't queued, so it is judged again, fresh, on the next frame.
 */
class AlertPolicy(private val config: HazardWatchConfig = HazardWatchConfig()) {
    private val scorer = HazardScorer(config)

    /** What was last said about each track. */
    private val said = mutableMapOf<Int, Said>()
    private var lastAlertMs: Long? = null

    /**
     * The alert to speak now for [tracks] (HazardTracker's, from the frame taken
     * at [frameTimeMs]), or null to stay quiet.
     */
    fun next(tracks: List<Track>, nowMs: Long, frameTimeMs: Long = nowMs): Alert? {
        said.keys.retainAll(tracks.map { it.id }.toSet())
        if (nowMs - frameTimeMs > config.staleMs) return null

        val candidates = tracks.mapNotNull { track ->
            val scored = scorer.score(track, said[track.id]?.band) ?: return@mapNotNull null
            val confirmed = track.isConfirmed(config) || scored.urgency == Urgency.CRITICAL
            scored.takeIf {
                confirmed &&
                    (config.includeNotices || it.urgency > Urgency.NOTICE) &&
                    worthSaying(it, said[track.id], nowMs)
            }
        }
        val top = candidates.maxWithOrNull(compareBy<Scored> { it.urgency }.thenBy { -it.track.depth }) ?: return null

        val gapOk = lastAlertMs?.let { nowMs - it >= config.minGapMs } ?: true
        if (!gapOk && top.urgency != Urgency.CRITICAL) return null

        val group = candidates.filter {
            it.track.name == top.track.name && it.side == top.side && it.band == top.band && it.urgency == top.urgency
        }
        group.forEach { scored ->
            val before = said[scored.track.id]
            val repeats = if (scored.urgency == Urgency.CRITICAL && before?.urgency == Urgency.CRITICAL) before.criticalRepeats + 1 else 0
            said[scored.track.id] = Said(
                band = maxOf(scored.band, before?.band ?: scored.band),
                inPath = scored.inPath,
                urgency = scored.urgency,
                atMs = nowMs,
                criticalRepeats = repeats,
            )
        }
        lastAlertMs = nowMs
        return Alert(top.urgency, top.track.name, group.size, top.side, top.band)
    }

    /** Forgets what was said, as when the watch stops. */
    fun clear() {
        said.clear()
        lastAlertMs = null
    }

    private fun worthSaying(scored: Scored, before: Said?, nowMs: Long): Boolean {
        if (before == null) return true
        val since = nowMs - before.atMs
        return when {
            scored.band > before.band -> true
            scored.inPath && !before.inPath -> true
            scored.urgency > before.urgency -> true
            scored.urgency == Urgency.CRITICAL ->
                since >= config.criticalRepeatMs && before.criticalRepeats < config.criticalMaxRepeats - 1
            scored.urgency == Urgency.WARNING && scored.inPath -> since >= config.warningRepeatMs
            else -> false
        }
    }

    private data class Said(
        val band: Band,
        val inPath: Boolean,
        val urgency: Urgency,
        val atMs: Long,
        /** How many times a critical alert was repeated after the first. */
        val criticalRepeats: Int,
    )
}
