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

    // ---------------------------------------------------- Tier S: balance tasks

    /** Glasses samples at 60 Hz on their own clock, arriving 20-60 ms later on the recording clock. */
    private class Feed(val device: DoubleArray, val phone: DoubleArray, val source: DoubleArray)

    private fun feed(durationS: Double): Feed {
        val random = Random(11)
        val n = (durationS * 60).toInt()
        val device = DoubleArray(n) { it * 1000.0 / 60 }
        return Feed(device, DoubleArray(n) { device[it] + 20 + random.nextDouble(0.0, 40.0) }, DoubleArray(n))
    }

    /** Rate of a 0 → 1 minimum-jerk step over [0, 1], per unit time; peaks at 1.875. */
    private fun minJerkRate(s: Double) = if (s <= 0 || s >= 1) 0.0 else 30 * s * s * (1 - s) * (1 - s)

    // The ten impulses of analysis/make_synthetic.py: + is left; the fourth is slow (131 deg/s).
    private val sides = intArrayOf(1, -1, -1, 1, -1, 1, 1, -1, 1, -1)
    private val amplitudes = doubleArrayOf(18.0, 16.0, 20.0, 14.0, 17.0, 19.0, 15.0, 18.0, 16.0, 20.0)
    private val durations = doubleArrayOf(0.15, 0.14, 0.16, 0.20, 0.15, 0.13, 0.15, 0.17, 0.14, 0.16)
    private val tones = doubleArrayOf(3_000.0, 6_100.0, 9_400.0, 12_000.0, 15_300.0, 18_200.0, 21_500.0,
        24_100.0, 27_600.0, 30_400.0)

    /** Yaw rate (rad/s): a quick turn 200 ms after each tone, held, then a slow return. */
    private fun impulseYaw(f: Feed): DoubleArray = DoubleArray(f.device.size) { i ->
        val t = f.device[i] / 1000
        var dps = 0.0
        for (k in tones.indices) {
            val onset = tones[k] / 1000 + 0.2
            val out = minJerkRate((t - onset) / durations[k]) / durations[k]
            val back = minJerkRate((t - (onset + durations[k] + 0.3)) / 1.2) / 1.2
            dps += sides[k] * amplitudes[k] * (out - back)
        }
        dps * PI / 180
    }

    @Test
    fun findsEachHeadImpulseWithItsSideAndSpeed() {
        val f = feed(40.0)
        val look = QuickLook.headImpulses(f.phone, f.device, impulseYaw(f), f.source, tones)
        assertEquals(10, look.tones)
        assertEquals(5, look.left)
        assertEquals(5, look.right)
        assertEquals(1, look.slow)
        // True peaks are 1.875 x amplitude / duration; their median is 214.3 deg/s.
        assertEquals(214.3, look.medianPeakDps!!, 214.3 * 0.03)
    }

    @Test
    fun aToneWithNoTurnAfterItIsMissedAndTheSlowReturnsDoNotCount() {
        val f = feed(40.0)
        val extra = tones + 35_000.0
        val look = QuickLook.headImpulses(f.phone, f.device, impulseYaw(f), f.source, extra)
        assertEquals(11, look.tones)
        assertEquals(10, look.detected)
    }

    @Test
    fun neuralBandSamplesNeverCountAsHeadImpulses() {
        val f = feed(40.0)
        val yaw = impulseYaw(f)
        for (i in yaw.indices step 7) {
            f.source[i] = 1.0
            yaw[i] = -10.0 // a wrist flick, far faster than any head turn
        }
        val look = QuickLook.headImpulses(f.phone, f.device, yaw, f.source, tones)
        assertEquals(5, look.left)
        assertEquals(5, look.right)
    }

    @Test
    fun balanceHeadSpeedLeavesOutTheStartAndTheStepThatEndedTheHold() {
        val f = feed(12.0)
        // Steady sway of 2 deg/s RMS, a 40 deg/s settling burst in the first
        // second and the step that ended the hold in its last 0.8 s.
        val gx = DoubleArray(f.device.size) { i ->
            val t = f.device[i] / 1000
            val dps = if (t < 1.0 || t > 11.2) 40.0 else 2.0 * kotlin.math.sqrt(2.0) * sin(2 * PI * 0.5 * t)
            dps * PI / 180
        }
        val zeros = DoubleArray(f.device.size)
        val lost = QuickLook.balance(f.phone, f.device, gx, zeros, zeros, f.source, 0.0, 12_000.0, lost = true)
        assertEquals(12.0, lost.holdSec, 1e-9)
        assertTrue(lost.lost)
        assertEquals(2.0, lost.headSpeedRmsDps!!, 0.1)
        // Held to the end, the last second is part of the hold and the burst counts.
        val held = QuickLook.balance(f.phone, f.device, gx, zeros, zeros, f.source, 0.0, 12_000.0, lost = false)
        assertTrue("${held.headSpeedRmsDps}", held.headSpeedRmsDps!! > 10.0)
        // Lost after 6 s, the window would be 3 s: too short, so only the length is reported.
        val short = QuickLook.balance(f.phone, f.device, gx, zeros, zeros, f.source, 0.0, 6_000.0, lost = true)
        assertEquals(6.0, short.holdSec, 1e-9)
        assertNull(short.headSpeedRmsDps)
    }
}
