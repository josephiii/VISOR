package ucf.visor.motionlab.analysis

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class FftTest {

    @Test
    fun matchesDirectDft() {
        val n = 16
        val random = Random(7)
        val re = DoubleArray(n) { random.nextDouble(-1.0, 1.0) }
        val im = DoubleArray(n) { random.nextDouble(-1.0, 1.0) }
        val expectedRe = DoubleArray(n)
        val expectedIm = DoubleArray(n)
        for (k in 0 until n) {
            for (t in 0 until n) {
                val angle = -2.0 * PI * k * t / n
                expectedRe[k] += re[t] * cos(angle) - im[t] * sin(angle)
                expectedIm[k] += re[t] * sin(angle) + im[t] * cos(angle)
            }
        }
        Fft(n).transform(re, im)
        for (k in 0 until n) {
            assertEquals(expectedRe[k], re[k], 1e-9)
            assertEquals(expectedIm[k], im[k], 1e-9)
        }
    }

    @Test
    fun twoDimensionalRoundTripIsIdentity() {
        val w = 32
        val h = 64
        val random = Random(11)
        val original = DoubleArray(w * h) { random.nextDouble() }
        val re = original.copyOf()
        val im = DoubleArray(w * h)
        val fft = Fft2d(w, h)
        fft.transform(re, im)
        fft.transform(re, im, inverse = true)
        for (i in original.indices) {
            assertEquals(original[i], re[i], 1e-9)
            assertEquals(0.0, im[i], 1e-9)
        }
    }

    @Test
    fun deltaHasFlatSpectrum() {
        val w = 8
        val h = 4
        val re = DoubleArray(w * h).also { it[0] = 1.0 }
        val im = DoubleArray(w * h)
        Fft2d(w, h).transform(re, im)
        for (i in re.indices) {
            assertEquals(1.0, re[i], 1e-12)
            assertEquals(0.0, im[i], 1e-12)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPowerOfTwo() {
        Fft(12)
    }
}
