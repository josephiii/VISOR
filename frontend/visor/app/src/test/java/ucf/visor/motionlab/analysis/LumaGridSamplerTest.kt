package ucf.visor.motionlab.analysis

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LumaGridSamplerTest {

    @Test
    fun fitsTheLargestCentredCropForEachStreamQuality() {
        val sampler = LumaGridSampler()
        // MWDAT's three stream sizes, portrait.
        for ((w, h, scale) in listOf(Triple(360, 640, 2.5), Triple(504, 896, 3.5), Triple(720, 1280, 5.0))) {
            val g = sampler.geometryFor(w, h)
            assertEquals(128, g.gridWidth)
            assertEquals(256, g.gridHeight)
            assertEquals(scale, g.scale, 1e-12)
            assertEquals((w - 128 * scale) / 2, g.cropX, 1e-12)
            assertEquals(0.0, g.cropY, 1e-12)
        }
    }

    @Test
    fun landscapeFramesGetALandscapeGrid() {
        val g = LumaGridSampler().geometryFor(1280, 720)
        assertEquals(256, g.gridWidth)
        assertEquals(128, g.gridHeight)
        assertEquals(5.0, g.scale, 1e-12)
    }

    @Test
    fun averagesEachCellAndLeavesTheBufferPositionAlone() {
        val w = 360
        val h = 640
        // Luma encodes the column: a horizontal ramp. Chroma planes follow.
        val frame = ByteBuffer.allocate(w * h * 3 / 2)
        for (y in 0 until h) for (x in 0 until w) frame.put(y * w + x, (x % 256).toByte())
        frame.position(0)
        val out = DoubleArray(128 * 256)
        val g = LumaGridSampler().sample(frame, w, h, out)!!
        assertEquals(0, frame.position())
        // Cell 0 covers source columns [20, 22.5) -> 20 and 21, mean 20.5.
        assertEquals(20.5, out[0], 1e-9)
        // Every row sees the same ramp, and the ramp only increases (until it wraps).
        for (gx in 1 until 90) assertTrue(out[gx] > out[gx - 1])
        assertEquals(out[5], out[200 * g.gridWidth + 5], 1e-9)
    }

    @Test
    fun rejectsABufferShorterThanItsLumaPlane() {
        val out = DoubleArray(128 * 256)
        assertNull(LumaGridSampler().sample(ByteBuffer.allocate(100), 360, 640, out))
    }
}
