package ucf.visor.motionlab

import com.meta.wearable.dat.motion.types.MotionSource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticMotionTest {

    private val samples = SyntheticMotion.yawOscillation(durationSec = 2.0, rateHz = 60, frequencyHz = 1.0, amplitudeDeg = 15.0)

    @Test
    fun isEvenlySampledGlassesData() {
        assertEquals(120, samples.size)
        assertEquals(0L, samples.first().timestampNs)
        assertEquals(1_000_000_000L / 60, samples[1].timestampNs)
        assertTrue(samples.all { it.source == MotionSource.GLASSES && it.magnetometer == null })
    }

    @Test
    fun gravityStaysOnTheUpAxisDuringPureYaw() {
        for (s in samples) {
            val a = s.accelerometer!!
            assertEquals(9.80665f, a.y, 1e-4f)
            assertEquals(0f, a.x, 0f)
            assertEquals(0f, a.z, 0f)
        }
    }

    @Test
    fun gyroscopeIntegratesToTheQuaternionYaw() {
        var integrated = 0.0
        for (i in 1 until samples.size) {
            val dt = (samples[i].timestampNs - samples[i - 1].timestampNs) / 1e9
            integrated += 0.5 * (samples[i].gyroscope!!.y + samples[i - 1].gyroscope!!.y) * dt
            val q = samples[i].orientation!!
            val norm = sqrt((q.w * q.w + q.x * q.x + q.y * q.y + q.z * q.z).toDouble())
            assertEquals(1.0, norm, 1e-5)
            val quaternionYaw = 2 * asin(q.y.toDouble())
            assertEquals("yaw at sample $i", quaternionYaw, integrated, 2e-3)
        }
        val peakRate = samples.maxOf { abs(it.gyroscope!!.y.toDouble()) }
        assertEquals(15 * PI / 180 * 2 * PI, peakRate, 0.01)
    }
}
