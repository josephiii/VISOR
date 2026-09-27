package ucf.visor.motionlab.analysis

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ground truth for the IMU-versus-image fit: a synthetic 1 Hz yaw oscillation,
 * sampled by a 60 Hz gyroscope on one clock and seen by a 30 fps camera on
 * another, delivered to the "phone" with random transport delays. The image
 * lags the head by a known amount and moves a known number of pixels per
 * degree; the quick look must find both.
 */
class QuickLookTest {

    private data class Synthetic(
        val motionPhone: DoubleArray,
        val motionDevice: DoubleArray,
        val gx: DoubleArray,
        val gy: DoubleArray,
        val gz: DoubleArray,
        val source: DoubleArray,
        val videoPhone: DoubleArray,
        val videoPts: DoubleArray,
        val shiftX: DoubleArray,
        val shiftY: DoubleArray,
        val peak: DoubleArray,
        val texture: DoubleArray,
        val ref: DoubleArray,
    )

    private fun synthesize(
        lagMs: Double,
        pixelsPerDegree: Double,
        amplitudeDeg: Double = 15.0,
        frequencyHz: Double = 1.0,
        durationS: Double = 30.0,
        imageNoisePx: Double = 0.3,
        neuralBandEvery: Int = 0,
    ): Synthetic {
        val random = Random(3)
        val omega = 2 * PI * frequencyHz
        val amplitudeRad = amplitudeDeg * PI / 180

        // Head yaw angle theta(t) = A sin(wt); the gyro reads d(theta)/dt about +Y.
        fun angle(tSec: Double) = amplitudeRad * sin(omega * tSec)
        fun rate(tSec: Double) = amplitudeRad * omega * cos(omega * tSec)

        // Glasses clock: arbitrary origin. Phone receives each sample 20-60 ms later.
        val motionClockOffset = 1_234_567.0
        val mCount = (durationS * 60).toInt()
        val mDevice = DoubleArray(mCount) { it * 1000.0 / 60 }
        val mPhone = DoubleArray(mCount) { mDevice[it] + motionClockOffset + 20 + random.nextDouble(0.0, 40.0) }
        val gy = DoubleArray(mCount) { rate(mDevice[it] / 1000) + random.nextDouble(-0.005, 0.005) }
        val gx = DoubleArray(mCount) { random.nextDouble(-0.005, 0.005) }
        val gz = DoubleArray(mCount) { random.nextDouble(-0.005, 0.005) }
        val source = DoubleArray(mCount) { if (neuralBandEvery > 0 && it % neuralBandEvery == 0) 1.0 else 0.0 }
        // A Neural Band sample carries its own (wrist) rotation, which must not
        // leak into the head fit.
        for (i in 0 until mCount) if (source[i] == 1.0) gy[i] = 5.0

        // Camera clock: another origin, and the image of true time t is
        // stamped at t + lag. The phone receives frames 40-90 ms after stamping.
        val videoClockOffset = -777.0
        val vCount = (durationS * 30).toInt() - 2
        val vPts = DoubleArray(vCount) { it * 1000.0 / 30 }
        val vPhone = DoubleArray(vCount) {
            vPts[it] - videoClockOffset + motionClockOffset + 40 + random.nextDouble(0.0, 50.0)
        }
        // Content moves opposite to the camera's rotation: turning left (+yaw)
        // slides the scene right in the image... expressed here as a negative gain.
        val shiftX = DoubleArray(vCount) { Double.NaN }
        val ref = DoubleArray(vCount) { Double.NaN }
        for (c in 1 until vCount) {
            val tA = (vPts[c - 1] - videoClockOffset - lagMs) / 1000
            val tB = (vPts[c] - videoClockOffset - lagMs) / 1000
            val dTheta = Math.toDegrees(angle(tB) - angle(tA))
            shiftX[c] = -pixelsPerDegree * dTheta + random.nextDouble(-imageNoisePx, imageNoisePx)
            ref[c] = (c - 1).toDouble()
        }
        return Synthetic(
            motionPhone = mPhone, motionDevice = mDevice, gx = gx, gy = gy, gz = gz, source = source,
            videoPhone = vPhone, videoPts = vPts, shiftX = shiftX,
            shiftY = DoubleArray(vCount) { if (it == 0) Double.NaN else random.nextDouble(-0.3, 0.3) },
            peak = DoubleArray(vCount) { if (it == 0) Double.NaN else 0.6 },
            texture = DoubleArray(vCount) { 30.0 },
            ref = ref,
        )
    }

    private fun run(s: Synthetic) = QuickLook.analyze(
        s.motionPhone, s.motionDevice, s.gx, s.gy, s.gz, s.source,
        s.videoPhone, s.videoPts, s.shiftX, s.shiftY, s.peak, s.texture, s.ref,
    )

    @Test
    fun findsTheLagScaleAndAxes() {
        for (lag in listOf(0.0, 45.0, 120.0)) {
            val result = run(synthesize(lagMs = lag, pixelsPerDegree = 6.0))
            assertNotNull("fit at lag $lag; notes: ${result.notes}", result.fit)
            val fit = result.fit!!
            assertEquals("gyro axis", "y", fit.gyroAxis)
            assertEquals("image axis", "x", fit.imageAxis)
            assertTrue("negative gain expected, r=${fit.correlation}", fit.correlation < -0.98)
            assertEquals("pixels per degree at lag $lag", 6.0, fit.pixelsPerDegree, 0.2)
            // The lower-envelope mapping leaves a bias of (min video delay - min motion
            // delay) = 40 - 20 = 20 ms, which the lag search absorbs.
            assertEquals("lag at $lag", lag + 20.0, fit.lagMs, 6.0)
            assertTrue("residual ${fit.residualDps}", fit.residualDps < 5.0)
        }
    }

    @Test
    fun ignoresNeuralBandSamples() {
        val s = synthesize(lagMs = 60.0, pixelsPerDegree = 8.0, neuralBandEvery = 7)
        val result = run(s)
        assertNotNull("notes: ${result.notes}", result.fit)
        assertEquals(8.0, result.fit!!.pixelsPerDegree, 0.3)
        assertTrue(result.notes.any { it.contains("not from the glasses") })
        // Timing is computed from the glasses' own samples only.
        val glassesSamples = s.source.count { it == 0.0 }
        assertEquals(glassesSamples, result.motion!!.samples)
        assertTrue("rate ${result.motion.effectiveHz}", abs(result.motion.effectiveHz - 60.0 * 6 / 7) < 1.0)
    }

    @Test
    fun marksAFitUnreliableWhenTheImageIgnoresTheHead() {
        // The scene moves on its own (a steady pan, as MockDeviceKit's test video
        // does) while the head shakes: the numbers must not be presented as a fit.
        val s = synthesize(lagMs = 50.0, pixelsPerDegree = 6.0)
        val random = Random(9)
        val pan = DoubleArray(s.shiftX.size) { if (it == 0) Double.NaN else -6.0 + random.nextDouble(-0.3, 0.3) }
        val result = run(s.copy(shiftX = pan))
        assertNotNull(result.fit)
        assertTrue("r=${result.fit!!.correlation}", !result.fit.reliable)
    }

    @Test
    fun aGoodFitIsReliable() {
        assertTrue(run(synthesize(lagMs = 30.0, pixelsPerDegree = 6.0)).fit!!.reliable)
    }

    @Test
    fun declinesToFitWhenTheHeadIsStill() {
        val s = synthesize(lagMs = 50.0, pixelsPerDegree = 6.0, amplitudeDeg = 0.01)
        val result = run(s)
        assertNull(result.fit)
        assertTrue(result.notes.any { it.contains("Too little head rotation") })
    }

    @Test
    fun declinesToFitWhenTrackingIsPoor() {
        val s = synthesize(lagMs = 50.0, pixelsPerDegree = 6.0)
        val result = run(s.copy(peak = DoubleArray(s.peak.size) { 0.05 }))
        assertNull(result.fit)
        assertEquals(0.0, result.trackedFraction!!, 1e-9)
        assertTrue(result.notes.any { it.contains("tracked reliably") })
    }

    @Test
    fun timingCountsDropoutsFromTheStreamsOwnClock() {
        val t = DoubleArray(100) { it * 16.7 }.toMutableList()
        // Remove five samples in a row: one gap of 6 intervals.
        repeat(5) { t.removeAt(50) }
        val timing = QuickLook.timing(t.toDoubleArray())!!
        assertEquals(95, timing.samples)
        assertEquals(1, timing.dropouts)
        assertEquals(16.7 * 6, timing.longestGapMs, 1e-9)
        assertEquals(16.7, timing.medianIntervalMs, 1e-9)
    }

    @Test
    fun integratesAndInterpolates() {
        val times = doubleArrayOf(0.0, 1000.0, 2000.0)
        val cumulative = QuickLook.cumulativeIntegral(times, doubleArrayOf(1.0, 1.0, 3.0))
        assertEquals(1.0, cumulative[1], 1e-12)
        assertEquals(3.0, cumulative[2], 1e-12)
        assertEquals(2.0, QuickLook.interpolate(times, cumulative, 1500.0), 1e-12)
        // Arrival minus stamp: -5.0 and -5.5; the envelope is the smaller.
        assertEquals(-5.5, QuickLook.lowerEnvelopeOffset(doubleArrayOf(5.0, 7.0), doubleArrayOf(10.0, 12.5))!!, 1e-12)
    }
}
