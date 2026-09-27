package ucf.visor.motionlab.analysis

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/** Sample-rate stability of one stream, from its own (device) timestamps. */
data class StreamTiming(
    val samples: Int,
    val effectiveHz: Double,
    val medianIntervalMs: Double,
    /** Intervals longer than 3x the median: the same dropout rule as the web analysis. */
    val dropouts: Int,
    val longestGapMs: Double,
)

/**
 * How the camera's image motion lines up with the head rotation the gyroscope
 * measured over the same intervals — the on-phone preview of the discrepancy
 * analysis in analysis/visor_imu/vor.py.
 *
 * @property gyroAxis the glasses axis carrying most of the rotation ("x", "y", "z").
 * @property imageAxis the image axis carrying most of the shift ("x" or "y").
 * @property lagMs how much later the image motion appears than the head motion
 *   that caused it, after both clocks are mapped onto the phone's. Positive:
 *   image lags IMU.
 * @property correlation Pearson r between image velocity and head angular
 *   velocity at that lag. Its sign is the axis convention, its magnitude how
 *   well one explains the other.
 * @property pixelsPerDegree image shift per degree of head rotation — for a
 *   pure rotation, the camera's focal length in pixels x pi/180.
 * @property residualDps what the fitted line leaves unexplained, as angular
 *   velocity (deg/s RMS): the discrepancy between IMU-measured head movement
 *   and the image shift, after the best linear fit.
 * @property intervalsUsed frame intervals that passed the quality gates.
 */
data class ImageMotionFit(
    val gyroAxis: String,
    val imageAxis: String,
    val lagMs: Double,
    val correlation: Double,
    val pixelsPerDegree: Double,
    val residualDps: Double,
    val headSpeedRmsDps: Double,
    val intervalsUsed: Int,
) {
    /**
     * Whether the head rotation explains enough of the image motion for the
     * lag and scale to mean anything. The same threshold as the Python
     * analysis (R² 0.5): below it the numbers describe noise and are withheld.
     */
    val reliable: Boolean get() = correlation * correlation >= QuickLook.MIN_RELIABLE_R2
}

data class QuickLookResult(
    val motion: StreamTiming?,
    val video: StreamTiming?,
    /** Share of frame intervals whose image shift passed the quality gates. */
    val trackedFraction: Double?,
    val fit: ImageMotionFit?,
    val notes: List<String>,
)

/**
 * The summary a tester hears and sees the moment a trial ends — enough to know
 * whether to keep the recording or run it again. The full, figure-producing
 * analysis is the Python toolkit's job; this deliberately does less, with the
 * same definitions so the two never disagree about what a number means.
 *
 * Clock handling: motion carries the glasses' monotonic clock, video its own
 * presentation timestamps, and the SDK does not say whether those are the same
 * clock. Each stream is therefore mapped onto the phone's arrival clock by its
 * lower envelope — arrival = device time + offset + transport delay, with delay
 * never negative, so min(arrival − device) recovers the offset up to that
 * stream's fastest delivery. What remains between the streams is found by the
 * lag search, not assumed.
 */
object QuickLook {

    /** Frame pairs whose correlation peak is below this are not trusted. */
    const val MIN_PEAK = 0.15

    /** Grids flatter than this (luma SD, 0–255) are too textureless to track. */
    const val MIN_TEXTURE_SD = 4.0

    /** Share of image motion head rotation must explain before a fit is reported. */
    const val MIN_RELIABLE_R2 = 0.5

    private const val MIN_INTERVALS = 30
    private const val MAX_LAG_MS = 300.0
    private const val LAG_STEP_MS = 2.0

    /** Below this RMS head speed the trial had too little rotation to fit. */
    private const val MIN_HEAD_SPEED_DPS = 3.0

    private const val SOURCE_GLASSES = 0.0

    fun analyze(
        motionPhoneMs: DoubleArray,
        motionDeviceMs: DoubleArray,
        gyroX: DoubleArray,
        gyroY: DoubleArray,
        gyroZ: DoubleArray,
        motionSource: DoubleArray,
        videoPhoneMs: DoubleArray,
        videoPtsMs: DoubleArray,
        shiftX: DoubleArray,
        shiftY: DoubleArray,
        peak: DoubleArray,
        textureSd: DoubleArray,
        refIndex: DoubleArray,
    ): QuickLookResult {
        val notes = mutableListOf<String>()

        // Only samples from the glasses themselves: a Neural Band is a second
        // rigid body, and mixing it into a head-rotation signal describes neither.
        val keep = motionSource.indices.filter {
            motionSource[it] == SOURCE_GLASSES &&
                motionDeviceMs[it].isFinite() && motionPhoneMs[it].isFinite()
        }
        if (keep.size < motionSource.size) {
            notes += "${motionSource.size - keep.size} motion samples were not from the glasses and were set aside."
        }
        val mDevice = DoubleArray(keep.size) { motionDeviceMs[keep[it]] }
        val mPhone = DoubleArray(keep.size) { motionPhoneMs[keep[it]] }
        val motionTiming = timing(mDevice)
        if (motionTiming == null) notes += "No usable motion samples were recorded."

        val videoTiming = if (videoPtsMs.isEmpty()) null else timing(videoPtsMs)

        var trackedFraction: Double? = null
        var fit: ImageMotionFit? = null
        if (videoTiming != null && motionTiming != null) {
            val intervals = frameIntervals(videoPhoneMs, videoPtsMs, shiftX, shiftY, peak, textureSd, refIndex)
            val analyzable = refIndex.count { it.isFinite() }
            trackedFraction = if (analyzable > 0) intervals.size.toDouble() / analyzable else 0.0
            if (intervals.size < MIN_INTERVALS) {
                notes += "Only ${intervals.size} frame pairs tracked reliably — point the glasses " +
                    "at a scene with more detail, in good light."
            } else {
                val gyro = arrayOf(
                    DoubleArray(keep.size) { gyroX[keep[it]] },
                    DoubleArray(keep.size) { gyroY[keep[it]] },
                    DoubleArray(keep.size) { gyroZ[keep[it]] },
                )
                fit = fitImageToHead(mDevice, mPhone, gyro, intervals, notes)
            }
        }
        return QuickLookResult(motionTiming, videoTiming, trackedFraction, fit, notes)
    }

    /** Timing statistics from a stream's own timestamps (ms). */
    fun timing(timesMs: DoubleArray): StreamTiming? {
        val t = timesMs.filter { it.isFinite() }
        if (t.size < 3) return null
        val dt = (1 until t.size).map { t[it] - t[it - 1] }.filter { it > 0 }
        if (dt.isEmpty()) return null
        val median = median(dt)
        val span = t.last() - t.first()
        return StreamTiming(
            samples = t.size,
            effectiveHz = if (span > 0) (t.size - 1) * 1000.0 / span else 0.0,
            medianIntervalMs = median,
            dropouts = dt.count { it > 3 * median },
            longestGapMs = dt.max(),
        )
    }

    /** A frame-to-frame image displacement over a phone-clock interval. */
    internal data class Interval(val startMs: Double, val endMs: Double, val vx: Double, val vy: Double)

    private fun frameIntervals(
        videoPhoneMs: DoubleArray,
        videoPtsMs: DoubleArray,
        shiftX: DoubleArray,
        shiftY: DoubleArray,
        peak: DoubleArray,
        textureSd: DoubleArray,
        refIndex: DoubleArray,
    ): List<Interval> {
        val offset = lowerEnvelopeOffset(videoPhoneMs, videoPtsMs) ?: return emptyList()
        val out = ArrayList<Interval>()
        for (c in refIndex.indices) {
            val r = refIndex[c]
            if (!r.isFinite()) continue
            val ref = r.toInt()
            if (ref !in videoPtsMs.indices || ref >= c) continue
            if (!(peak[c] >= MIN_PEAK) || !(textureSd[c] >= MIN_TEXTURE_SD)) continue
            if (!shiftX[c].isFinite() || !shiftY[c].isFinite()) continue
            val start = videoPtsMs[ref] + offset
            val end = videoPtsMs[c] + offset
            val dtSec = (end - start) / 1000.0
            if (!(dtSec > 0)) continue
            out += Interval(start, end, shiftX[c] / dtSec, shiftY[c] / dtSec)
        }
        return out
    }

    private fun fitImageToHead(
        deviceMs: DoubleArray,
        phoneMs: DoubleArray,
        gyro: Array<DoubleArray>,
        intervals: List<Interval>,
        notes: MutableList<String>,
    ): ImageMotionFit? {
        val offset = lowerEnvelopeOffset(phoneMs, deviceMs) ?: return null
        val times = DoubleArray(deviceMs.size) { deviceMs[it] + offset }

        // The axis with the most rotation, and the image axis with the most motion.
        val axisNames = listOf("x", "y", "z")
        val gyroAxis = (0..2).maxBy { variance(gyro[it]) }
        val rate = gyro[gyroAxis]
        val headRmsDps = sqrt(meanSquare(rate)) * 180.0 / PI
        if (headRmsDps < MIN_HEAD_SPEED_DPS) {
            notes += "Too little head rotation to relate to the image " +
                "(%.1f deg/s RMS).".format(headRmsDps)
            return null
        }
        val cumulative = cumulativeIntegral(times, rate)

        // One fixed set of intervals for every candidate lag — those the IMU
        // covers even at the extremes of the search — so the correlations being
        // compared are over the same frames. The streams start and stop a little
        // apart, and the first and last frames are the ones this drops.
        val covered = intervals.filter {
            it.startMs - MAX_LAG_MS >= times.first() && it.endMs + MAX_LAG_MS <= times.last()
        }
        if (covered.size < MIN_INTERVALS) {
            notes += "The motion recording does not cover enough of the video's time span."
            return null
        }
        val vx = DoubleArray(covered.size) { covered[it].vx }
        val vy = DoubleArray(covered.size) { covered[it].vy }
        val imageIsX = variance(vx) >= variance(vy)
        val image = if (imageIsX) vx else vy

        var bestLag = 0.0
        var bestR = 0.0
        var bestHead: DoubleArray? = null
        var lag = -MAX_LAG_MS
        while (lag <= MAX_LAG_MS + 1e-9) {
            val head = meanRates(times, cumulative, covered, lag)
            val r = pearson(image, head)
            if (abs(r) > abs(bestR)) {
                bestR = r
                bestLag = lag
                bestHead = head
            }
            lag += LAG_STEP_MS
        }
        val head = bestHead ?: return null
        val slope = covariance(image, head) / variance(head) // image px/s per rad/s = px/rad
        if (!slope.isFinite() || slope == 0.0) return null
        var sumSq = 0.0
        for (i in head.indices) {
            val residual = image[i] / slope - head[i]
            sumSq += residual * residual
        }
        if (abs(bestLag) >= MAX_LAG_MS - LAG_STEP_MS) {
            notes += "The best lag sits at the edge of the search window; the clocks may be " +
                "further apart than expected — check in the full analysis."
        }
        return ImageMotionFit(
            gyroAxis = axisNames[gyroAxis],
            imageAxis = if (imageIsX) "x" else "y",
            lagMs = bestLag,
            correlation = bestR,
            pixelsPerDegree = abs(slope) * PI / 180.0,
            residualDps = sqrt(sumSq / head.size) * 180.0 / PI,
            headSpeedRmsDps = headRmsDps,
            intervalsUsed = head.size,
        )
    }

    /**
     * Mean angular velocity over each interval, with the image assumed to lag
     * the IMU by [lagMs] (so the head motion is looked for [lagMs] earlier).
     */
    private fun meanRates(
        times: DoubleArray,
        cumulative: DoubleArray,
        intervals: List<Interval>,
        lagMs: Double,
    ): DoubleArray = DoubleArray(intervals.size) { i ->
        val a = intervals[i].startMs - lagMs
        val b = intervals[i].endMs - lagMs
        (interpolate(times, cumulative, b) - interpolate(times, cumulative, a)) / ((b - a) / 1000.0)
    }

    /** Trapezoidal running integral of [rate] (per second) over [timesMs]. */
    internal fun cumulativeIntegral(timesMs: DoubleArray, rate: DoubleArray): DoubleArray {
        val out = DoubleArray(timesMs.size)
        for (i in 1 until timesMs.size) {
            val a = if (rate[i - 1].isFinite()) rate[i - 1] else 0.0
            val b = if (rate[i].isFinite()) rate[i] else 0.0
            out[i] = out[i - 1] + 0.5 * (a + b) * (timesMs[i] - timesMs[i - 1]) / 1000.0
        }
        return out
    }

    /** Linear interpolation of [values] at [t] over ascending [times]. */
    internal fun interpolate(times: DoubleArray, values: DoubleArray, t: Double): Double {
        var lo = 0
        var hi = times.size - 1
        if (t <= times[lo]) return values[lo]
        if (t >= times[hi]) return values[hi]
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (times[mid] <= t) lo = mid else hi = mid
        }
        val span = times[hi] - times[lo]
        if (span <= 0) return values[lo]
        return values[lo] + (values[hi] - values[lo]) * (t - times[lo]) / span
    }

    /**
     * The offset that maps a stream's own clock onto the phone clock:
     * min(arrival − stamped). See the class comment for why the minimum.
     */
    internal fun lowerEnvelopeOffset(phoneMs: DoubleArray, streamMs: DoubleArray): Double? {
        var best = Double.POSITIVE_INFINITY
        for (i in phoneMs.indices) {
            val d = phoneMs[i] - streamMs[i]
            if (d.isFinite() && d < best) best = d
        }
        return if (best.isFinite()) best else null
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else 0.5 * (sorted[n / 2 - 1] + sorted[n / 2])
    }

    private fun mean(v: DoubleArray): Double {
        var s = 0.0
        var n = 0
        for (x in v) if (x.isFinite()) { s += x; n++ }
        return if (n > 0) s / n else 0.0
    }

    private fun meanSquare(v: DoubleArray): Double {
        var s = 0.0
        var n = 0
        for (x in v) if (x.isFinite()) { s += x * x; n++ }
        return if (n > 0) s / n else 0.0
    }

    private fun variance(v: DoubleArray): Double {
        val m = mean(v)
        var s = 0.0
        var n = 0
        for (x in v) if (x.isFinite()) { s += (x - m) * (x - m); n++ }
        return if (n > 1) s / (n - 1) else 0.0
    }

    private fun covariance(a: DoubleArray, b: DoubleArray): Double {
        val ma = mean(a)
        val mb = mean(b)
        var s = 0.0
        for (i in a.indices) s += (a[i] - ma) * (b[i] - mb)
        return if (a.size > 1) s / (a.size - 1) else 0.0
    }

    private fun pearson(a: DoubleArray, b: DoubleArray): Double {
        val denominator = sqrt(variance(a) * variance(b))
        return if (denominator > 0) covariance(a, b) / denominator else 0.0
    }
}
