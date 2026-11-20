package ucf.visor.lookaround

import ucf.visor.motion.HeadingTracker
import ucf.visor.motion.SteadyGate
import kotlin.math.abs
import kotlin.math.sign

/**
 * "Guide me to the chair": leads the head round to face something Look around
 * found, by tone ([GuideTone]) and a few words, and says when it is straight
 * ahead. Pure and fed one pose at a time, like [ScanSequencer].
 *
 * The head is facing it once it is within [ON_TARGET_DEG] and holds still
 * there briefly; a head that swings past by more than [OVERSHOOT_SAY_DEG]
 * hears which way to come back, once per pass.
 */
class GuideSequencer(val target: Sighting) {

    sealed interface Action {
        object Arrived : Action

        /** Turned past it: come back, to the left when [toLeft]. */
        data class Overshot(val toLeft: Boolean) : Action
        object TimedOut : Action
    }

    private var startedAtMs: Long? = null

    /** The side (+1 left, -1 right) the wearer was last told to turn toward. */
    private var toldSide = 0.0
    private val gate = SteadyGate(thresholdDegPerSec = ARRIVE_STILL_DEG_PER_SEC, holdMs = ARRIVE_HOLD_MS)

    /** How far to turn, left positive, as of the last pose; null before the first. */
    var errorDeg: Double? = null
        private set

    var isDone = false
        private set

    fun onPose(pose: HeadingTracker.Pose, nowMs: Long): List<Action> {
        if (isDone) return emptyList()
        val started = startedAtMs ?: nowMs.also { startedAtMs = it }
        val error = target.relativeTo(pose.yawDeg)
        errorDeg = error
        val side = sign(error)
        // The first instruction (LookAroundPhrases.guideStart) names the side it is on now.
        if (toldSide == 0.0) toldSide = side

        if (abs(error) <= ON_TARGET_DEG) {
            if (gate.update(pose.atNanos, pose.speedDegPerSec)) return finish(Action.Arrived)
        } else {
            gate.reset()
            // Swung past it: it is now on the other side from where they were told to turn.
            if (side != toldSide && abs(error) > OVERSHOOT_SAY_DEG) {
                toldSide = side
                return listOf(Action.Overshot(toLeft = error > 0))
            }
        }
        if (nowMs - started >= TIMEOUT_MS) return finish(Action.TimedOut)
        return emptyList()
    }

    private fun finish(action: Action): List<Action> {
        isDone = true
        return listOf(action)
    }

    companion object {
        /** Within this, it is straight ahead: about the middle third of the camera's view. */
        const val ON_TARGET_DEG = 7.0

        /** Swinging past by more than this is said. */
        const val OVERSHOOT_SAY_DEG = 15.0

        /** Facing it needs only a brief pause, not a photo-sharp hold. */
        const val ARRIVE_STILL_DEG_PER_SEC = 20.0
        const val ARRIVE_HOLD_MS = 250L

        const val TIMEOUT_MS = 25_000L
    }
}

/**
 * The guidance tone: a short beep that comes faster and higher the nearer the
 * head is to facing where it should, like a parking sensor. Pitch and rate
 * both carry it, so it still works for someone who hears one of them poorly,
 * and the pitch stays in 440–880 Hz, below the high frequencies age-related
 * hearing loss takes first (as Chimes does).
 */
object GuideTone {
    const val LOW_HZ = 440.0
    const val HIGH_HZ = 880.0
    const val SLOWEST_MS = 700L
    const val FASTEST_MS = 110L

    /** How far off (degrees) counts as "far": anything further beeps at the slowest. */
    const val FAR_DEG = 90.0

    /** 0 when facing it, 1 when [FAR_DEG] or more away. */
    private fun farness(errorDeg: Double): Double = (abs(errorDeg) / FAR_DEG).coerceIn(0.0, 1.0)

    /** The gap between beeps, in milliseconds. */
    fun intervalMs(errorDeg: Double): Long =
        (FASTEST_MS + (SLOWEST_MS - FASTEST_MS) * farness(errorDeg)).toLong()

    /** The beep's pitch, in hertz. */
    fun pitchHz(errorDeg: Double): Double = HIGH_HZ - (HIGH_HZ - LOW_HZ) * farness(errorDeg)

    /** Which of [steps] prepared pitches, 0 the lowest, a beep [errorDeg] off uses. */
    fun pitchStep(errorDeg: Double, steps: Int): Int =
        ((1 - farness(errorDeg)) * (steps - 1)).toInt().coerceIn(0, steps - 1)
}
