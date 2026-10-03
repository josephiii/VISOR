package ucf.visor.motionlab.analysis

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * In-place iterative radix-2 complex FFT over split real/imaginary arrays.
 *
 * Pure Kotlin on purpose: the head-motion lab needs one thing from a signal
 * library — the transform behind phase correlation — and pulling in a native
 * dependency for it would put a JNI boundary in the per-frame hot path. Twiddle
 * factors and the bit-reversal permutation are computed once per size and
 * reused for every row, column and frame.
 */
class Fft(val size: Int) {

    init {
        require(size >= 2 && size and (size - 1) == 0) { "FFT size must be a power of two: $size" }
    }

    private val levels = Integer.numberOfTrailingZeros(size)
    private val cosTable = DoubleArray(size / 2) { cos(2.0 * PI * it / size) }
    private val sinTable = DoubleArray(size / 2) { sin(2.0 * PI * it / size) }
    private val reversed = IntArray(size) { Integer.reverse(it) ushr (32 - levels) }

    /**
     * Transforms [re]/[im] in place, reading and writing [size] elements that
     * start at [offset] and are [stride] apart — which is what lets the 2-D
     * transform run over matrix columns without copying them out.
     *
     * [inverse] uses the conjugate twiddles and does **not** scale by 1/N;
     * callers that need the normalized inverse divide once at the end.
     */
    fun transform(
        re: DoubleArray,
        im: DoubleArray,
        offset: Int = 0,
        stride: Int = 1,
        inverse: Boolean = false,
    ) {
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                val a = offset + i * stride
                val b = offset + j * stride
                val tr = re[a]; re[a] = re[b]; re[b] = tr
                val ti = im[a]; im[a] = im[b]; im[b] = ti
            }
        }
        val sign = if (inverse) 1.0 else -1.0
        var half = 1
        while (half < size) {
            val tableStep = size / (half * 2)
            var start = 0
            while (start < size) {
                var k = 0
                for (j in 0 until half) {
                    val wr = cosTable[k]
                    val wi = sign * sinTable[k]
                    val a = offset + (start + j) * stride
                    val b = offset + (start + j + half) * stride
                    val xr = re[b] * wr - im[b] * wi
                    val xi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                    k += tableStep
                }
                start += half * 2
            }
            half *= 2
        }
    }
}

/**
 * 2-D FFT over a row-major [width] x [height] matrix, as separable row then
 * column passes. Both dimensions must be powers of two.
 */
class Fft2d(val width: Int, val height: Int) {
    private val rows = Fft(width)
    private val cols = Fft(height)

    fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean = false) {
        require(re.size == width * height && im.size == width * height) {
            "Expected ${width}x$height values"
        }
        for (y in 0 until height) rows.transform(re, im, offset = y * width, inverse = inverse)
        for (x in 0 until width) cols.transform(re, im, offset = x, stride = width, inverse = inverse)
        if (inverse) {
            val scale = 1.0 / (width * height)
            for (i in re.indices) {
                re[i] *= scale
                im[i] *= scale
            }
        }
    }
}
