package ucf.visor.hazards

/**
 * Every threshold the hazard watch uses on the phone, in one place. What needs
 * only one frame (confidence, which classes, how far, merging boxes) is filtered
 * on the server instead: backend/services/HazardWatchFilter.py.
 *
 * The defaults follow documentation/research/ambient-hazard-detection.md.
 */
data class HazardWatchConfig(
    // --- The loop (HazardWatch) ---
    /** At most one check this often: 2 a second, as fast as things approach at walking pace. */
    val checkIntervalMs: Long = 500,
    /** With no frame checked for this long, the watch says it can't see. */
    val cantSeeMs: Long = 3_000,
    /** At the start, opening the glasses' camera may take this long before it counts as not seeing. */
    val startGraceMs: Long = 10_000,
    /** While it still can't see, it says so again this often, so a long silence is never taken for a clear path. */
    val cantSeeReminderMs: Long = 60_000,

    // --- Tracking: the same object from frame to frame (HazardTracker) ---
    /** A detection continues a track of its class when their boxes overlap this much. */
    val matchIou: Float = 0.3f,
    /** Confirmed once seen in [confirmHits] of the last [confirmWindow] frames: one-frame flickers never speak. */
    val confirmHits: Int = 2,
    val confirmWindow: Int = 3,
    /** A track not seen for this long is gone. If it comes back, it is new and is announced again. */
    val dropAfterMs: Long = 2_000,

    // --- Scoring (HazardScorer) ---
    /** Distance bands, in meters: under [veryCloseM] very close, under [closeM] close, under [aheadM] ahead. */
    val veryCloseM: Float = 1.0f,
    val closeM: Float = 2.5f,
    val aheadM: Float = 4.0f,
    /** A move into a closer band counts only this far past its edge, so an object hovering on an edge doesn't repeat. */
    val bandHysteresisM: Float = 0.2f,
    /** In the walking path: box center in the middle [pathWidth] of the frame, and box reaching the lower half. */
    val pathWidth: Float = 0.4f,
    /** Approaching: distance shrinking faster than this (m/s), or box height growing faster than [growthPerSec] (share/s). */
    val approachMps: Float = 0.5f,
    val growthPerSec: Float = 0.15f,
    /** A drop (stairs, curb, hole) in the path is critical within this distance, not only when very close. */
    val dropCriticalM: Float = 2f,
    /** A vehicle approaching with less time to contact than this is critical. */
    val criticalContactS: Float = 3f,
    /** A person or vehicle approaching from any side within this range is a warning. */
    val approachWarnM: Float = 4f,

    // --- Speaking (AlertPolicy) ---
    /** At most one alert every this long. A new critical alert doesn't wait. */
    val minGapMs: Long = 1_500,
    /** A frame whose answer arrives later than this is not spoken about: late is worse than nothing. */
    val staleMs: Long = 1_000,
    /** A warning still in the path is said once more after this long. */
    val warningRepeatMs: Long = 8_000,
    /** A critical alert repeats this often while it stays critical, at most [criticalMaxRepeats] times. */
    val criticalRepeatMs: Long = 2_000,
    val criticalMaxRepeats: Int = 3,
    /** Notices (off to the side, or farther ahead) are off by default. */
    val includeNotices: Boolean = false,
)
