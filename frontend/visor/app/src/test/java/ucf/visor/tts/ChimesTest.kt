package ucf.visor.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ucf.visor.tts.Chimes.Chime

/** What tells the session chimes apart, and keeps them out of the way. */
class ChimesTest {

    @Test
    fun aSessionStartRisesAndItsEndFallsThroughTheSameNotes() {
        val up = Chime.SESSION_START.notes.map { it.frequency }
        val down = Chime.SESSION_END.notes.map { it.frequency }

        assertEquals("the start rises", up.sorted(), up)
        assertEquals("the end falls back through the same notes", up.reversed(), down)
    }

    @Test
    fun chimesAreShortEnoughNotToHoldUpWhatIsSaidNext() {
        // "Starting session" waits for its chime to end.
        Chime.entries.forEach { assertTrue("$it lasts ${it.durationMs} ms", it.durationMs <= 450) }
    }

    @Test
    fun chimesStayBelowTheFrequenciesHearingLossTakesFirst() {
        Chime.entries.flatMap { it.notes }.forEach {
            assertTrue("${it.frequency} Hz", it.frequency in 500.0..1_000.0)
        }
    }
}
