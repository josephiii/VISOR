package ucf.visor.motionlab.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseCorrelatorTest {

    private val width = 128
    private val height = 256
    private val scene = SyntheticScene(width = 640, height = 800, seed = 42)

    /** Content displacement (dx, dy) between two views cut from the scene. */
    private fun measure(dx: Double, dy: Double, noise: Double = 0.0): ShiftEstimate {
        val correlator = PhaseCorrelator(width, height)
        val originX = 200.0
        val originY = 200.0
        assertNull(correlator.push(scene.view(originX, originY, width, height, noise, seed = 1)))
        return correlator.push(scene.view(originX - dx, originY - dy, width, height, noise, seed = 2))!!
    }

    @Test
    fun identicalFramesHaveNoShiftAndFullPeak() {
        val estimate = measure(0.0, 0.0)
        assertEquals(0.0, estimate.dx, 0.02)
        assertEquals(0.0, estimate.dy, 0.02)
        assertTrue("peak ${estimate.peak}", estimate.peak > 0.95)
    }

    @Test
    fun recoversIntegerShiftsWithTheRightSign() {
        for ((dx, dy) in listOf(5.0 to -3.0, -12.0 to 7.0, 20.0 to 0.0, 0.0 to -25.0)) {
            val estimate = measure(dx, dy)
            assertEquals("dx for ($dx, $dy)", dx, estimate.dx, 0.1)
            assertEquals("dy for ($dx, $dy)", dy, estimate.dy, 0.1)
        }
    }

    @Test
    fun recoversSubPixelShifts() {
        for ((dx, dy) in listOf(2.3 to -1.6, -0.4 to 0.7, 7.75 to 3.25)) {
            val estimate = measure(dx, dy)
            assertEquals("dx for ($dx, $dy)", dx, estimate.dx, 0.15)
            assertEquals("dy for ($dx, $dy)", dy, estimate.dy, 0.15)
        }
    }

    @Test
    fun toleratesSensorNoise() {
        val estimate = measure(6.4, -2.2, noise = 12.0)
        assertEquals(6.4, estimate.dx, 0.25)
        assertEquals(-2.2, estimate.dy, 0.25)
        assertTrue("peak ${estimate.peak}", estimate.peak > 0.15)
    }

    @Test
    fun flagsATexturelessSceneInsteadOfInventingAShift() {
        val correlator = PhaseCorrelator(width, height)
        val flat = DoubleArray(width * height) { 128.0 }
        correlator.push(flat)
        val estimate = correlator.push(flat)!!
        assertEquals(0.0, estimate.textureSd, 1e-9)
        assertTrue("peak ${estimate.peak}", estimate.peak < QuickLook.MIN_PEAK)
    }

    @Test
    fun eachFrameIsMeasuredAgainstThePreviousOne() {
        val correlator = PhaseCorrelator(width, height)
        correlator.push(scene.view(200.0, 200.0, width, height))
        val first = correlator.push(scene.view(197.0, 200.0, width, height))!!
        val second = correlator.push(scene.view(192.0, 201.0, width, height))!!
        assertEquals(3.0, first.dx, 0.1)
        assertEquals(5.0, second.dx, 0.1)
        assertEquals(-1.0, second.dy, 0.1)
    }

    @Test
    fun subPixelFitStaysWithinHalfAPixelAndLeansTowardTheLargerNeighbour() {
        assertEquals(0.0, PhaseCorrelator.subPixel(0.5, 1.0, 0.5), 1e-12)
        assertTrue(PhaseCorrelator.subPixel(0.2, 1.0, 0.9) > 0)
        assertTrue(PhaseCorrelator.subPixel(0.9, 1.0, 0.2) < 0)
        // Negative neighbours (possible in a correlation surface) use the parabola.
        assertTrue(PhaseCorrelator.subPixel(-0.3, 1.0, 0.4) > 0)
        val random = kotlin.random.Random(5)
        repeat(1_000) {
            val center = random.nextDouble(0.1, 1.0)
            val left = random.nextDouble(-0.2, center)
            val right = random.nextDouble(-0.2, center)
            val offset = PhaseCorrelator.subPixel(left, center, right)
            assertTrue("offset $offset for ($left, $center, $right)", offset in -0.5..0.5)
            if (right > left) assertTrue(offset >= 0) else if (left > right) assertTrue(offset <= 0)
        }
    }
}
