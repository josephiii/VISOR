package ucf.visor.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ucf.visor.tts.Chimes.Chime

/** What makes the ready chime recognizable, and keeps it out of the way. */
class ChimesTest {

    @Test
    fun theReadyChimeRises() {
        val notes = Chime.READY.notes.map { it.frequency }

        assertEquals(notes.sorted(), notes)
    }

    @Test
    fun chimesAreShortEnoughNotToHoldUpWhatIsSaidNext() {
        // "VISOR is ready." waits for its chime to end.
        Chime.entries.forEach { assertTrue("$it lasts ${it.durationMs} ms", it.durationMs <= 450) }
    }

    @Test
    fun chimesStayBelowTheFrequenciesHearingLossTakesFirst() {
        Chime.entries.flatMap { it.notes }.forEach {
            assertTrue("${it.frequency} Hz", it.frequency in 500.0..1_000.0)
        }
    }
}
