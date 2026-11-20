package ucf.visor.lookaround

import ucf.visor.motion.HeadingTracker
import ucf.visor.motion.SteadyGate
import kotlin.math.abs
import kotlin.math.sign

/**
 * The steps of one Look around, decided from the head's movement alone: which
 * way to turn next, when the head has reached a stop and held still there long
 * enough for a sharp picture, when to give up on a stop, and when it is over.
 *
 * Pure, and fed one pose at a time ([onPose]), so the whole scan is tested on
 * synthetic head movement with no glasses and no clock. LookAround carries out
 * what it returns: says the cues, takes the pictures, plays the tone toward
 * [target].
 *
 * **Reaching a stop.** The head is "there" from [TOLERANCE_DEG] short of the
 * stop to [OVERSHOOT_DEG] beyond it in the direction of travel. A picture taken
 * anywhere in that window is fine: its actual direction is recorded with it,
 * so the map never assumes the stop was hit exactly.
 *
 * **Holding still.** The picture waits for the head to hold still
 * ([SteadyGate], the lab's measured threshold), which is also what makes its
 * direction trustworthy: the head isn't moving while the photo is taken. A
 * head that can't settle within [RELAX_AFTER_MS] hears "Hold still", and the
 * threshold loosens ([SteadyGate.RELAXED_DEG_PER_SEC]), so a tremor never
 * leaves a stop without its picture.
 *
 * **Giving up.** A stop not reached in [STOP_TIMEOUT_MS] is skipped, and the
 * answer says which side was not seen, rather than the scan waiting forever on
 * a neck that won't turn that far.
 */
class ScanSequencer(private val plan: ScanPlan) {

    /** What VISOR says between pictures. */
    enum class Cue {
        /** "Hold your head still." At the start, for the picture ahead. */
        HOLD_AHEAD,
        TURN_LEFT,
        KEEP_LEFT,
        TURN_RIGHT,
        KEEP_RIGHT,

        /** At a stop, with a head that hasn't settled. */
        HOLD_STILL,

        /** Every picture is taken: back to where they started. */
        FACE_AHEAD,
    }

    sealed interface Action {
        data class Say(val cue: Cue) : Action

        /** Take the picture for stop [stop] now; the head is at [yawDeg]. */
        data class Capture(val stop: Int, val yawDeg: Double) : Action

        /** Stop [stop] (at [stopYawDeg]) was given up on. */
        data class Missed(val stop: Int, val stopYawDeg: Double) : Action

        /** The scan is over; the head now faces [yawDeg]. */
        data class Finished(val yawDeg: Double) : Action
    }

    private enum class Phase { SEEK, CAPTURING, RETURN, DONE }

    private var phase = Phase.SEEK
    private var stop = 0
    private var enteredAtMs: Long? = null
    private var inWindowSinceMs: Long? = null
    private var holdSaid = false
    private var pendingCue: Cue? = Cue.HOLD_AHEAD
    private val gate = SteadyGate()
    private var lastYaw = 0.0

    /** The direction the tone leads toward right now, or null when there is none (a picture is being taken). */
    val target: Double?
        get() = when (phase) {
            Phase.SEEK -> plan.stops[stop]
            Phase.RETURN -> 0.0
            Phase.CAPTURING, Phase.DONE -> null
        }

    val isDone: Boolean get() = phase == Phase.DONE

    /** Adds one head pose, read at [nowMs] on the phone's clock. Returns what to do now. */
    fun onPose(pose: HeadingTracker.Pose, nowMs: Long): List<Action> {
        lastYaw = pose.yawDeg
        val actions = mutableListOf<Action>()
        pendingCue?.let {
            actions += Action.Say(it)
            pendingCue = null
        }
        when (phase) {
            Phase.SEEK -> seek(pose, nowMs, actions)
            Phase.RETURN -> returnAhead(pose, nowMs, actions)
            Phase.CAPTURING, Phase.DONE -> Unit
        }
        return actions
    }

    /** The picture asked for by [Action.Capture] is done, or failed. Returns what to do now. */
    fun onCaptureDone(taken: Boolean): List<Action> {
        if (phase != Phase.CAPTURING) return emptyList()
        val actions = mutableListOf<Action>()
        if (!taken) actions += Action.Missed(stop, plan.stops[stop])
        advance(actions)
        return actions
    }

    private fun seek(pose: HeadingTracker.Pose, nowMs: Long, actions: MutableList<Action>) {
        val entered = enteredAtMs ?: nowMs.also { enteredAtMs = it }
        val targetYaw = plan.stops[stop]
        val direction = travelDirection(stop)
        if (inWindow(pose.yawDeg, targetYaw, direction)) {
            val since = inWindowSinceMs ?: nowMs.also { inWindowSinceMs = it }
            if (nowMs - since >= RELAX_AFTER_MS) {
                gate.thresholdDegPerSec = SteadyGate.RELAXED_DEG_PER_SEC
                if (!holdSaid) {
                    holdSaid = true
                    actions += Action.Say(Cue.HOLD_STILL)
                }
            }
            if (gate.update(pose.atNanos, pose.speedDegPerSec)) {
                phase = Phase.CAPTURING
                actions += Action.Capture(stop, pose.yawDeg)
                return
            }
        } else {
            inWindowSinceMs = null
            gate.reset()
        }
        if (nowMs - entered >= STOP_TIMEOUT_MS) {
            actions += Action.Missed(stop, targetYaw)
            advance(actions)
        }
    }

    private fun returnAhead(pose: HeadingTracker.Pose, nowMs: Long, actions: MutableList<Action>) {
        val entered = enteredAtMs ?: nowMs.also { enteredAtMs = it }
        val facingAhead = abs(pose.yawDeg) <= TOLERANCE_DEG
        val settled = if (facingAhead) gate.update(pose.atNanos, pose.speedDegPerSec) else false.also { gate.reset() }
        if (settled || nowMs - entered >= RETURN_TIMEOUT_MS) {
            phase = Phase.DONE
            actions += Action.Finished(pose.yawDeg)
        }
    }

    /** On to the next stop, or back to ahead after the last. */
    private fun advance(actions: MutableList<Action>) {
        enteredAtMs = null
        inWindowSinceMs = null
        holdSaid = false
        gate.reset()
        gate.thresholdDegPerSec = SteadyGate.STILL_DEG_PER_SEC
        if (stop + 1 < plan.stops.size) {
            val previousDirection = travelDirection(stop)
            stop++
            phase = Phase.SEEK
            val direction = travelDirection(stop)
            actions += Action.Say(
                when {
                    direction > 0 && previousDirection > 0 -> Cue.KEEP_LEFT
                    direction > 0 -> Cue.TURN_LEFT
                    direction < 0 && previousDirection < 0 -> Cue.KEEP_RIGHT
                    else -> Cue.TURN_RIGHT
                },
            )
        } else {
            phase = Phase.RETURN
            gate.thresholdDegPerSec = SteadyGate.RELAXED_DEG_PER_SEC
            if (abs(lastYaw) > TOLERANCE_DEG) actions += Action.Say(Cue.FACE_AHEAD)
        }
    }

    /** +1 when stop [index] is reached by turning left, -1 by turning right, 0 for the first (ahead). */
    private fun travelDirection(index: Int): Double =
        if (index == 0) 0.0 else sign(plan.stops[index] - plan.stops[index - 1])

    private fun inWindow(yaw: Double, target: Double, direction: Double): Boolean {
        if (direction == 0.0) return abs(yaw - target) <= TOLERANCE_DEG
        val past = (yaw - target) * direction
        return past >= -TOLERANCE_DEG && past <= OVERSHOOT_DEG
    }

    companion object {
        /** How far short of a stop the head may be and count as there. */
        const val TOLERANCE_DEG = 10.0

        /** How far past a stop it may be and still count. */
        const val OVERSHOOT_DEG = 25.0

        /** At a stop this long without settling: "Hold still", and a looser threshold. */
        const val RELAX_AFTER_MS = 2_500L

        /** A stop not pictured in this long is given up on. */
        const val STOP_TIMEOUT_MS = 12_000L

        /** How long the wearer has to face ahead again at the end. */
        const val RETURN_TIMEOUT_MS = 6_000L
    }
}
