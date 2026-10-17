package ucf.visor.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The volume sliders in Settings: ten-percent notches, and how a value is shown and spoken. */
class VolumesTest {

    @Test
    fun aDraggedValueSnapsToTheNearestTenPercent() {
        assertEquals(70, Volumes.percent(Volumes.snap(0.68f, Volumes.MinSpeech)))
        assertEquals(30, Volumes.percent(Volumes.snap(0.33f, Volumes.MinSoundEffects)))
    }

    @Test
    fun theVoiceCanBeTurnedDownButNeverOff() {
        assertEquals(Volumes.MinSpeech, Volumes.snap(0f, Volumes.MinSpeech), TOLERANCE)
        assertEquals("10%", Volumes.label(Volumes.snap(0f, Volumes.MinSpeech)))
    }

    @Test
    fun soundEffectsCanBeTurnedOff() {
        val off = Volumes.snap(0.03f, Volumes.MinSoundEffects)
        assertEquals("Off", Volumes.label(off))
        assertEquals("off", Volumes.spokenLabel(off))
    }

    @Test
    fun nothingGoesPastFullVolume() {
        assertEquals(Volumes.Max, Volumes.snap(1.4f, Volumes.MinSpeech), TOLERANCE)
    }

    @Test
    fun aScreenReaderHearsTheRealPercentage() {
        assertEquals("80 percent", Volumes.spokenLabel(0.8f))
    }

    @Test
    fun eachSliderHasOneNotchPerTenPercent() {
        // 10%, 20% … 100%: ten positions, so eight notches between the ends.
        assertEquals(8, Volumes.sliderSteps(Volumes.MinSpeech))
        // Off, 10% … 100%: eleven positions, nine notches.
        assertEquals(9, Volumes.sliderSteps(Volumes.MinSoundEffects))
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
