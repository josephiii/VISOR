package ucf.visor.motion

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Where the head is pointing, turned left or right, relative to where it was
 * when tracking began, worked out from the glasses' gyroscope one sample at a
 * time. Pure Kotlin, so it is tested on the JVM with synthetic head movement.
 *
 * **Why the gyroscope, and not the glasses' own orientation.** The lab's
 * native recordings (documentation/testing/imu-pose-tracking-report.md) found
 * MWDAT's fused quaternion tracking head yaw well on Ray-Ban Display, but its
 * reference frame is undocumented, was chosen by a fit, and differs from what
 * MockDeviceKit replays. Integrating the gyroscope needs no convention beyond
 * the body frame the lab confirmed on real glasses (x right, y up, z backward;
 * a left turn is positive about +y). Its drift is the turn-on bias the lab
 * measured natively, 0.015 °/s: a third of a degree over a 20-second
 * look-around, far below the width of anything VISOR names.
 *
 * **Which axis "turning" is.** Turning the head to look around is rotation
 * about the vertical, which is the body's +y only while the head is level.
 * Looking 20° down, +y alone would read the turn 6% short. So the turn rate
 * is the angular velocity's component along "up", taken from the
 * accelerometer's gravity reading while it gives one. Many native recordings
 * carry no accelerometer (the characterization report lists it null), and then
 * "up" stays the body's +y, the lab's measured dominant yaw axis.
 *
 * **Speed.** The head's total angular speed, every axis, is what says it is
 * still: that is what blurs a photo ([SteadyGate]).
 */
class HeadingTracker {

    /** One reading of the head: [yawDeg] (left is positive) and how fast it is turning, at [atNanos]. */
    data class Pose(
        /** Turned left (positive) or right (negative) from where tracking began, in degrees. */
        val yawDeg: Double,
        /** Total angular speed, every axis, in degrees per second. */
        val speedDegPerSec: Double,
        /** The sample's time on the glasses' own clock. */
        val atNanos: Long,
    )

    private var lastNanos: Long? = null
    private var lastYawRate = 0.0
    private var yawRad = 0.0

    // The body-frame direction of "up", a unit vector.
    private var upX = 0.0
    private var upY = 1.0
    private var upZ = 0.0

    /** The latest pose, or null before the first sample. */
    var pose: Pose? = null
        private set

    /**
     * Adds one sample: [tNanos] on the glasses' clock, [gyro] in rad/s (x, y,
     * z), and [accel] in m/s² including gravity, when there is one. Returns
     * the pose after it.
     */
    fun update(tNanos: Long, gyro: FloatArray, accel: FloatArray? = null): Pose {
        accel?.let(::updateUp)
        val gx = gyro[0].toDouble()
        val gy = gyro[1].toDouble()
        val gz = gyro[2].toDouble()
        val yawRate = gx * upX + gy * upY + gz * upZ
        val previous = lastNanos
        if (previous != null) {
            val dt = (tNanos - previous) / 1e9
            // A clock that went back (MockDeviceKit loops its feed with the
            // feed's own timestamps) or a long gap (a paused session) is not
            // integrated across: the head's turn in it is unknown. A gap of up
            // to MAX_STEP_SEC is: Bluetooth delivers samples in bursts, but
            // their own timestamps stay evenly spaced.
            if (dt > 0 && dt <= MAX_STEP_SEC) yawRad += (lastYawRate + yawRate) / 2 * dt
        }
        lastNanos = tNanos
        lastYawRate = yawRate
        val speed = sqrt(gx * gx + gy * gy + gz * gz) * DEG_PER_RAD
        return Pose(yawRad * DEG_PER_RAD, speed, tNanos).also { pose = it }
    }

    /** Makes the head's current direction yaw 0: "ahead" from now on. */
    fun recenter() {
        yawRad = 0.0
        pose = pose?.copy(yawDeg = 0.0)
    }

    /**
     * Moves "up" toward the accelerometer's reading, if it is close to
     * gravity alone: while the head speeds up or slows down, the reading
     * includes that too, and is not used.
     */
    private fun updateUp(accel: FloatArray) {
        val ax = accel[0].toDouble()
        val ay = accel[1].toDouble()
        val az = accel[2].toDouble()
        val norm = sqrt(ax * ax + ay * ay + az * az)
        if (abs(norm - GRAVITY) > GRAVITY_TOLERANCE) return
        // At rest an accelerometer reads +1 g pointing up.
        val x = upX + UP_SMOOTHING * (ax / norm - upX)
        val y = upY + UP_SMOOTHING * (ay / norm - upY)
        val z = upZ + UP_SMOOTHING * (az / norm - upZ)
        val length = sqrt(x * x + y * y + z * z)
        if (length < 1e-6) return
        upX = x / length
        upY = y / length
        upZ = z / length
    }

    companion object {
        const val DEG_PER_RAD = 180.0 / PI
        private const val GRAVITY = 9.80665

        /** How far from 1 g a reading may be and still be taken as gravity alone. */
        private const val GRAVITY_TOLERANCE = 1.5

        /** How quickly "up" follows the accelerometer: about a third of a second at 30 Hz. */
        private const val UP_SMOOTHING = 0.1

        /** The longest gap between two samples that is still integrated across. */
        const val MAX_STEP_SEC = 0.5
    }
}
