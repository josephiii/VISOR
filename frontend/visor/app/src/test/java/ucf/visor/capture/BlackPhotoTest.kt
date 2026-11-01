package ucf.visor.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A phone camera covered by a pocket, a table or a finger is told apart from
 * a dim room, so VISOR says the camera is covered only when it is.
 */
class BlackPhotoTest {

    private val side = BlackPhoto.SAMPLE_SIDE

    private fun gray(level: Int): Int = (0xFF shl 24) or (level shl 16) or (level shl 8) or level

    private fun photoOf(level: Int) = IntArray(side * side) { gray(level) }

    @Test
    fun aCoveredCameraIsBlack() {
        assertTrue(BlackPhoto.isBlack(photoOf(0)))
        // Sensor noise at the camera's highest gain.
        assertTrue(BlackPhoto.isBlack(IntArray(side * side) { gray(it % 12) }))
    }

    @Test
    fun aDimRoomWithALampIsNotBlack() {
        val dim = photoOf(6).also { it[side * 3 + 4] = gray(200) }
        assertFalse(BlackPhoto.isBlack(dim))
    }

    @Test
    fun anEvenlyDimPhotoIsNotBlack() {
        assertFalse(BlackPhoto.isBlack(photoOf(30)))
    }

    @Test
    fun anOrdinaryPhotoIsNotBlack() {
        assertFalse(BlackPhoto.isBlack(photoOf(128)))
        assertFalse(BlackPhoto.isBlack(IntArray(side * side) { gray(it % 256) }))
    }

    @Test
    fun noPixelsIsNotCalledBlack() {
        assertFalse(BlackPhoto.isBlack(IntArray(0)))
    }

    @Test
    fun greenCountsForMostOfHowBrightAPixelLooks() {
        assertEquals(149, BlackPhoto.luma(0xFF00FF00.toInt()))
        assertEquals(29, BlackPhoto.luma(0xFF0000FF.toInt()))
        assertEquals(255, BlackPhoto.luma(0xFFFFFFFF.toInt()))
    }
}
