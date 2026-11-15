package ucf.visor.motionlab

import com.meta.wearable.dat.motion.types.MotionSample
import com.meta.wearable.dat.motion.types.MotionSource
import com.meta.wearable.dat.motion.types.Quaternion
import com.meta.wearable.dat.motion.types.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Synthetic head motion for MockDeviceKit's motion feed
 * (`MockGlasses.services.motion.setMotionFeed`), so the head-motion lab can be
 * exercised end to end with no glasses at all.
 *
 * The body frame follows what Meta's BirdSpotter sample measured on worn
 * glasses: +Y points up (a still head reads +9.81 m/s² on Y), -Z points
 * forward, X is lateral. A head turning left is a positive rotation about +Y.
 *
 * MockDeviceKit replays a looped feed with the feed's own timestamps, so the
 * "device clock" jumps back at every loop, which real glasses' monotonic clock
 * never does. A feed for a lab trial must therefore outlast the trial.
 */
object SyntheticMotion {

    private const val GRAVITY = 9.80665f

    /**
     * A sinusoidal yaw oscillation — the movement of a paced VOR x1 exercise.
     *
     * @param amplitudeDeg peak yaw either side of centre.
     * @param frequencyHz oscillations per second (1 Hz = one left-right cycle).
     * @param rateHz samples per second (MWDAT Motion offers 5–60 Hz).
     */
    fun yawOscillation(
        durationSec: Double = 10.0,
        rateHz: Int = 60,
        frequencyHz: Double = 1.0,
        amplitudeDeg: Double = 15.0,
    ): List<MotionSample> {
        val omega = 2 * PI * frequencyHz
        val amplitude = amplitudeDeg * PI / 180
        val count = (durationSec * rateHz).toInt()
        return List(count) { i ->
            val t = i.toDouble() / rateHz
            val yaw = amplitude * sin(omega * t)
            val yawRate = amplitude * omega * cos(omega * t)
            MotionSample(
                timestampNs = (i * 1_000_000_000L) / rateHz,
                // Pure yaw about the gravity axis leaves gravity where it was.
                accelerometer = Vector3(0f, GRAVITY, 0f),
                gyroscope = Vector3(0f, yawRate.toFloat(), 0f),
                magnetometer = null,
                orientation = Quaternion(
                    x = 0f,
                    y = sin(yaw / 2).toFloat(),
                    z = 0f,
                    w = cos(yaw / 2).toFloat(),
                ),
                source = MotionSource.GLASSES,
            )
        }
    }

    /** One leg of a scripted head movement: turn to [yawDeg] over [moveSec], then hold still for [holdSec]. */
    data class Leg(val yawDeg: Double, val moveSec: Double, val holdSec: Double)

    /**
     * A wearer doing Look around (lookaround/LookAround), over and over: hold
     * still ahead, turn left past both left stops, then right past both right
     * stops, holding still at each, then face ahead again. It covers the wide
     * scan's stops and so the standard one's too. Each turn is a smooth
     * minimum-jerk move, and every hold is far under the lab's stillness
     * threshold, so a scan driven by it completes within a cycle or two
     * whatever moment it starts in.
     *
     * Long by default, so MockDeviceKit's looping (which jumps the clock back)
     * doesn't happen within a demo.
     */
    fun lookAround(
        durationSec: Double = 300.0,
        rateHz: Int = 30,
        legs: List<Leg> = LookAroundLegs,
    ): List<MotionSample> {
        val cycleSec = legs.sumOf { it.moveSec + it.holdSec }
        val count = (durationSec * rateHz).toInt()
        return List(count) { i ->
            val t = (i.toDouble() / rateHz) % cycleSec
            var from = legs.last().yawDeg
            var start = 0.0
            var yawDeg = from
            var rateDeg = 0.0
            for (leg in legs) {
                val moveEnd = start + leg.moveSec
                if (t < moveEnd) {
                    val s = (t - start) / leg.moveSec
                    val shape = s * s * s * (10 - 15 * s + 6 * s * s)
                    val slope = 30 * s * s * (1 - s) * (1 - s) / leg.moveSec
                    yawDeg = from + (leg.yawDeg - from) * shape
                    rateDeg = (leg.yawDeg - from) * slope
                    break
                }
                if (t < moveEnd + leg.holdSec) {
                    yawDeg = leg.yawDeg
                    rateDeg = 0.0
                    break
                }
                from = leg.yawDeg
                start = moveEnd + leg.holdSec
            }
            val yaw = yawDeg * PI / 180
            MotionSample(
                timestampNs = (i * 1_000_000_000L) / rateHz,
                accelerometer = Vector3(0f, GRAVITY, 0f),
                gyroscope = Vector3(0f, (rateDeg * PI / 180).toFloat(), 0f),
                magnetometer = null,
                orientation = Quaternion(x = 0f, y = sin(yaw / 2).toFloat(), z = 0f, w = cos(yaw / 2).toFloat()),
                source = MotionSource.GLASSES,
            )
        }
    }

    /** Ahead, 38° and 72° left, 38° and 72° right, ahead: past each of the wide scan's stops. */
    private val LookAroundLegs = listOf(
        Leg(yawDeg = 0.0, moveSec = 1.5, holdSec = 2.0),
        Leg(yawDeg = 38.0, moveSec = 1.5, holdSec = 2.0),
        Leg(yawDeg = 72.0, moveSec = 1.2, holdSec = 2.0),
        Leg(yawDeg = -38.0, moveSec = 2.5, holdSec = 2.0),
        Leg(yawDeg = -72.0, moveSec = 1.2, holdSec = 2.0),
        Leg(yawDeg = 0.0, moveSec = 2.0, holdSec = 3.0),
    )

    /**
     * Head impulses, alternately left and right, one every [periodSec]: a quick
     * minimum-jerk turn of [amplitudeDeg] over [turnSec] (peak speed
     * 1.875 x amplitude / turn time), a short hold, then a slow return that
     * stays under the lab's 60 deg/s impulse threshold. A tone at any moment
     * has an impulse within one period after it.
     */
    fun headImpulses(
        durationSec: Double = 2.0,
        periodSec: Double = 1.0,
        rateHz: Int = 60,
        amplitudeDeg: Double = 15.0,
        turnSec: Double = 0.15,
        returnSec: Double = 0.6,
    ): List<MotionSample> {
        fun step(s: Double) =
            s.coerceIn(0.0, 1.0).let { it * it * it * (10 - 15 * it + 6 * it * it) }

        fun rate(s: Double) = if (s <= 0 || s >= 1) 0.0 else 30 * s * s * (1 - s) * (1 - s)
        val amplitude = amplitudeDeg * PI / 180
        val count = (durationSec * rateHz).toInt()
        return List(count) { i ->
            val t = i.toDouble() / rateHz
            val side = if ((t / periodSec).toInt() % 2 == 0) 1.0 else -1.0
            val local = t % periodSec
            val backAt = turnSec + 0.15
            val yaw =
                side * amplitude * (step(local / turnSec) - step((local - backAt) / returnSec))
            val yawRate =
                side * amplitude * (rate(local / turnSec) / turnSec - rate((local - backAt) / returnSec) / returnSec)
            MotionSample(
                timestampNs = (i * 1_000_000_000L) / rateHz,
                accelerometer = Vector3(0f, GRAVITY, 0f),
                gyroscope = Vector3(0f, yawRate.toFloat(), 0f),
                magnetometer = null,
                orientation = Quaternion(
                    x = 0f,
                    y = sin(yaw / 2).toFloat(),
                    z = 0f,
                    w = cos(yaw / 2).toFloat()
                ),
                source = MotionSource.GLASSES,
            )
        }
    }
}
