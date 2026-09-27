package ucf.visor.motionlab.analysis

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * The global image shift between two consecutive analysis grids.
 *
 * @property dx horizontal shift in grid pixels; positive means the scene content
 *   moved toward +x (right) between the reference grid and this one.
 * @property dy vertical shift in grid pixels; positive means content moved
 *   toward +y (down, since grid rows run top to bottom).
 * @property peak height of the correlation peak, normalized so two identical
 *   grids score 1.0. Low values mean the shift is not trustworthy: a blank
 *   wall, heavy motion blur, a scene change, or overlap lost to a large turn.
 * @property textureSd standard deviation of this grid's luma (0–255 scale).
 *   A textureless scene gives phase correlation nothing to lock onto, so this is
 *   recorded next to the shift rather than assumed.
 */
data class ShiftEstimate(
    val dx: Double,
    val dy: Double,
    val peak: Double,
    val textureSd: Double,
)

/**
 * Frame-to-frame global translation by phase correlation.
 *
 * Why phase correlation: it measures the *whole-image* displacement a head
 * rotation causes, in one closed-form step, with sub-pixel precision and
 * without feature tracking — and it is insensitive to the global brightness
 * changes that auto-exposure produces while the wearer turns.
 *
 * The pipeline per frame:
 *  1. subtract the mean and apply a separable Hann window, so the frame's hard
 *     edges do not dominate the spectrum;
 *  2. forward FFT, then the normalized cross-power spectrum against the
 *     previous frame (phase only);
 *  3. a Gaussian spectral weight ([bandwidth] cycles/pixel) before the inverse
 *     FFT. Whitening the spectrum amplifies high-frequency compression noise
 *     from the glasses' video codec; the weight suppresses it and makes the
 *     correlation peak a smooth Gaussian, which is what makes step 4 accurate;
 *  4. the integer peak, refined to sub-pixel by a three-point Gaussian fit.
 *
 * A pure translation model: roll (rotation about the viewing axis) and parallax
 * from head translation are not modelled, and show up in [ShiftEstimate.peak]
 * rather than as a shift. For yaw and pitch — the axes VOR testing uses — a
 * distant scene moves almost purely by translation in the image.
 *
 * Not thread-safe; holds the previous frame's spectrum between calls.
 */
class PhaseCorrelator(
    val width: Int,
    val height: Int,
    private val bandwidth: Double = DEFAULT_BANDWIDTH,
) {
    private val size = width * height
    private val fft = Fft2d(width, height)
    private val window = DoubleArray(size)
    private val spectralWeight = DoubleArray(size)
    private val weightMean: Double

    private var refRe = DoubleArray(size)
    private var refIm = DoubleArray(size)
    private var curRe = DoubleArray(size)
    private var curIm = DoubleArray(size)
    private val workRe = DoubleArray(size)
    private val workIm = DoubleArray(size)
    private var hasReference = false

    init {
        val wx = DoubleArray(width) { hann(it, width) }
        val wy = DoubleArray(height) { hann(it, height) }
        var weightSum = 0.0
        for (y in 0 until height) {
            val fy = frequency(y, height)
            for (x in 0 until width) {
                val i = y * width + x
                window[i] = wx[x] * wy[y]
                val fx = frequency(x, width)
                val w = exp(-(fx * fx + fy * fy) / (2.0 * bandwidth * bandwidth))
                spectralWeight[i] = w
                weightSum += w
            }
        }
        weightMean = weightSum / size
    }

    /** Forgets the reference frame; the next [push] starts a new sequence. */
    fun reset() {
        hasReference = false
    }

    /**
     * Feeds the next grid (row-major, [width] x [height]).
     *
     * @return the shift of this grid relative to the previously pushed one, or
     *   null when there is no previous grid to compare against.
     */
    fun push(grid: DoubleArray): ShiftEstimate? {
        require(grid.size == size) { "Grid must be ${width}x$height" }

        var mean = 0.0
        for (v in grid) mean += v
        mean /= size
        var variance = 0.0
        for (i in 0 until size) {
            val d = grid[i] - mean
            variance += d * d
            curRe[i] = d * window[i]
            curIm[i] = 0.0
        }
        val textureSd = sqrt(variance / size)
        fft.transform(curRe, curIm)

        val estimate = if (hasReference) correlate(textureSd) else null

        // The current spectrum becomes the next frame's reference. Swapping
        // arrays instead of copying keeps the hot path allocation-free.
        val swapRe = refRe; refRe = curRe; curRe = swapRe
        val swapIm = refIm; refIm = curIm; curIm = swapIm
        hasReference = true
        return estimate
    }

    private fun correlate(textureSd: Double): ShiftEstimate {
        // Cross-power of current x conj(reference): its inverse peaks at +d when
        // the content moved by +d from the reference to the current frame.
        for (i in 0 until size) {
            val ar = curRe[i]
            val ai = curIm[i]
            val br = refRe[i]
            val bi = -refIm[i]
            val pr = ar * br - ai * bi
            val pi = ar * bi + ai * br
            val magnitude = sqrt(pr * pr + pi * pi)
            if (magnitude > MAGNITUDE_FLOOR) {
                val w = spectralWeight[i] / magnitude
                workRe[i] = pr * w
                workIm[i] = pi * w
            } else {
                workRe[i] = 0.0
                workIm[i] = 0.0
            }
        }
        fft.transform(workRe, workIm, inverse = true)

        var best = 0
        for (i in 1 until size) if (workRe[i] > workRe[best]) best = i
        val px = best % width
        val py = best / width

        val center = workRe[best]
        val dx = unwrap(px, width) + subPixel(
            workRe[py * width + (px - 1 + width) % width],
            center,
            workRe[py * width + (px + 1) % width],
        )
        val dy = unwrap(py, height) + subPixel(
            workRe[((py - 1 + height) % height) * width + px],
            center,
            workRe[((py + 1) % height) * width + px],
        )
        return ShiftEstimate(
            dx = dx,
            dy = dy,
            peak = (center / weightMean).coerceIn(0.0, 1.0),
            textureSd = textureSd,
        )
    }

    companion object {
        /**
         * Gaussian spectral weight, in cycles per grid pixel (Nyquist is 0.5).
         * 0.2 keeps the band where a compressed, downsampled video frame still
         * carries real texture, and gives a correlation peak about one pixel
         * wide — broad enough for the three-point fit, narrow enough to stay
         * precise.
         */
        const val DEFAULT_BANDWIDTH = 0.2

        private const val MAGNITUDE_FLOOR = 1e-9

        private fun hann(i: Int, n: Int): Double = 0.5 - 0.5 * cos(2.0 * PI * i / (n - 1))

        /** Signed frequency of FFT bin [k] of [n], in cycles per sample. */
        private fun frequency(k: Int, n: Int): Double =
            (if (k < n / 2) k else k - n).toDouble() / n

        /** FFT index to signed displacement: the upper half of the range wraps negative. */
        private fun unwrap(index: Int, n: Int): Double =
            (if (index < n / 2) index else index - n).toDouble()

        /**
         * Sub-pixel offset of a peak from three samples, by fitting a Gaussian
         * (a parabola through the logs). Falls back to a plain parabola when any
         * sample is not positive, and never moves the peak more than half a
         * pixel — beyond that, the integer peak was the wrong one.
         */
        internal fun subPixel(left: Double, center: Double, right: Double): Double {
            val offset = if (left > 0 && center > 0 && right > 0) {
                val l = ln(left)
                val c = ln(center)
                val r = ln(right)
                val denominator = l - 2 * c + r
                if (abs(denominator) < 1e-12) 0.0 else 0.5 * (l - r) / denominator
            } else {
                val denominator = left - 2 * center + right
                if (abs(denominator) < 1e-12) 0.0 else 0.5 * (left - right) / denominator
            }
            return offset.coerceIn(-0.5, 0.5)
        }
    }
}
