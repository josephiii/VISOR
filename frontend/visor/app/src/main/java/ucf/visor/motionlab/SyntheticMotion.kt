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
