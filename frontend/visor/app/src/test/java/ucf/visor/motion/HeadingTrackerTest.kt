package ucf.visor.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ucf.visor.motionlab.SyntheticMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Where the head points, from the gyroscope alone, on synthetic head movement with a known answer. */
class HeadingTrackerTest {

    private val rad = PI / 180

    /** [seconds] of turning about the vertical at [degPerSec], with the head pitched down [pitchDeg]. */
    private fun turn(
        tracker: HeadingTracker,
        seconds: Double,
        degPerSec: Double,
        pitchDeg: Double = 0.0,
        withAccel: Boolean = true,
        rateHz: Int = 30,
        startNanos: Long = 0,
    ): HeadingTracker.Pose {
        // "Up" in the body frame of a head pitched down: +y tilted toward +z (backward).
        val upY = cos(pitchDeg * rad)
        val upZ = sin(pitchDeg * rad)
        val w = degPerSec * rad
        var pose: HeadingTracker.Pose? = null
        val count = (seconds * rateHz).toInt()
        for (i in 0..count) {
            pose = tracker.update(
                tNanos = startNanos + i * 1_000_000_000L / rateHz,
                gyro = floatArrayOf(0f, (w * upY).toFloat(), (w * upZ).toFloat()),
                accel = if (withAccel) floatArrayOf(0f, (9.80665 * upY).toFloat(), (9.80665 * upZ).toFloat()) else null,
            )
        }
        return pose!!
    }

    @Test
    fun aLeftTurnIsPositiveAndIntegratesToItsAngle() {
        val pose = turn(HeadingTracker(), seconds = 2.0, degPerSec = 30.0)
        assertEquals(60.0, pose.yawDeg, 0.5)
        assertEquals(30.0, pose.speedDegPerSec, 0.01)
    }

    @Test
    fun aRightTurnIsNegative() {
        assertEquals(-45.0, turn(HeadingTracker(), seconds = 1.5, degPerSec = -30.0).yawDeg, 0.5)
    }

    @Test
    fun lookingDownStillMeasuresTheTurnAboutTheVertical() {
        // Pitched 40° down, the body's y axis alone sees cos(40°) = 77% of the turn.
        val withGravity = turn(HeadingTracker(), seconds = 2.0, degPerSec = 30.0, pitchDeg = 40.0)
        assertEquals(60.0, withGravity.yawDeg, 1.5)

        // With no accelerometer (as many native recordings), it falls back to the
        // body's y axis: short by that cosine, but still the right way round.
        val without = turn(HeadingTracker(), seconds = 2.0, degPerSec = 30.0, pitchDeg = 40.0, withAccel = false)
        assertEquals(60.0 * cos(40 * rad), without.yawDeg, 0.5)
    }

    @Test
    fun aClockThatJumpsBackOrALongGapIsNotIntegratedAcross() {
        val tracker = HeadingTracker()
        turn(tracker, seconds = 1.0, degPerSec = 30.0, startNanos = 10_000_000_000)
        // MockDeviceKit's loop: the clock goes back to 0.
        val afterLoop = tracker.update(0, floatArrayOf(0f, (30 * rad).toFloat(), 0f))
        assertEquals(30.0, afterLoop.yawDeg, 0.5)
        // A two-second gap (a paused session): the turn in it is unknown, not guessed.
        val afterGap = tracker.update(2_000_000_000, floatArrayOf(0f, (30 * rad).toFloat(), 0f))
        assertEquals(30.0, afterGap.yawDeg, 0.5)
    }

    @Test
    fun recenterMakesTheCurrentDirectionAhead() {
        val tracker = HeadingTracker()
        turn(tracker, seconds = 1.0, degPerSec = 40.0)
        tracker.recenter()
        val pose = turn(tracker, seconds = 1.0, degPerSec = -20.0, startNanos = 1_100_000_000)
        assertEquals(-20.0, pose.yawDeg, 1.5)
    }

    @Test
    fun theSyntheticLookAroundIntegratesToItsScript() {
        val tracker = HeadingTracker()
        // Exactly two cycles of the script.
        val cycleSec = 1.5 + 2 + 1.5 + 2 + 1.2 + 2 + 2.5 + 2 + 1.2 + 2 + 2 + 3
        val samples = SyntheticMotion.lookAround(durationSec = 2 * cycleSec)
        val yaws = samples.map { s ->
            tracker.update(
                s.timestampNs,
                floatArrayOf(s.gyroscope!!.x, s.gyroscope!!.y, s.gyroscope!!.z),
                s.accelerometer?.let { floatArrayOf(it.x, it.y, it.z) },
            ).yawDeg
        }
        // The script holds at 72° left and 72° right, and ends each cycle ahead.
        assertEquals(72.0, yaws.max(), 0.5)
        assertEquals(-72.0, yaws.min(), 0.5)
        assertEquals(0.0, yaws[(cycleSec * 30).toInt() - 1], 0.5)
        // Two cycles on, still ahead: no drift builds up from one to the next.
        assertEquals(0.0, yaws.last(), 0.5)
    }

    @Test
    fun theSteadyGateWaitsForAHoldAndForgetsOnMovement() {
        val gate = SteadyGate()
        val ms = 1_000_000L
        assertFalse(gate.update(0, 2.0))
        assertFalse(gate.update(200 * ms, 2.0))
        assertTrue(gate.update(300 * ms, 2.0))
        // One fast moment starts the hold again.
        assertFalse(gate.update(330 * ms, 30.0))
        assertFalse(gate.update(400 * ms, 2.0))
        assertFalse(gate.update(650 * ms, 2.0))
        assertTrue(gate.update(700 * ms, 2.0))
        // The lab's threshold: 6.4 °/s is not still.
        val strict = SteadyGate()
        assertFalse(strict.update(0, SteadyGate.STILL_DEG_PER_SEC))
        assertFalse(strict.update(500 * ms, SteadyGate.STILL_DEG_PER_SEC))
    }
}
