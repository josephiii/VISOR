package ucf.visor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The volume sliders in Settings: ten-percent notches, and how a value is shown and spoken. */
class VolumesTest {

    @Test
    fun aDraggedValueSnapsToTheNearestTenPercent() {
        assertEquals(70, Volumes.percent(Volumes.snap(0.68f)))
        assertEquals(30, Volumes.percent(Volumes.snap(0.33f)))
    }

    @Test
    fun everySliderCanBeTurnedOff() {
        val off = Volumes.snap(0.03f)
        assertTrue(Volumes.isOff(off))
        assertEquals("Off", Volumes.label(off))
        assertEquals("off", Volumes.spokenLabel(off))
    }

    @Test
    fun nothingGoesPastFullVolumeOrBelowSilence() {
        assertEquals(Volumes.Max, Volumes.snap(1.4f), TOLERANCE)
        assertEquals(Volumes.Off, Volumes.snap(-0.2f), TOLERANCE)
    }

    @Test
    fun aScreenReaderHearsTheRealPercentage() {
        assertEquals("80 percent", Volumes.spokenLabel(0.8f))
        assertEquals("80%", Volumes.label(0.8f))
    }

    @Test
    fun eachSliderHasOneNotchPerTenPercent() {
        // Off, 10% … 100%: eleven positions, so nine notches between the ends.
        assertEquals(9, Volumes.SliderSteps)
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
