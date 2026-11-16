package ucf.visor.hazards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ucf.visor.network.Hazard

class HazardAnnouncerTest {

    private val width = 1000
    private val announcer = HazardAnnouncer()

    /** A hazard in the left third (x 100-250) of a 1000 px wide picture. */
    private fun left(name: String, meters: Float) =
        Hazard(id = 0, coords = listOf(100f, 0f, 250f, 100f), depth = meters, name = name, conf = 0.9f)

    private fun at(seconds: Long, vararg hazards: Hazard) = announcer.next(hazards.toList(), width, seconds * 1000)

    @Test
    fun theSameHazardTwiceIsSpokenOnce() {
        assertEquals(1, at(0, left("chair", 2.4f)).size)
        assertTrue(at(3, left("chair", 2.4f)).isEmpty())
    }

    @Test
    fun movingWithinTheSameBucketIsNotNews() {
        at(0, left("chair", 2.4f)) // about 8 feet: close
        assertTrue(at(6, left("chair", 1.2f)).isEmpty()) // about 4 feet: still close
    }

    @Test
    fun aCloserBucketIsSpokenAgainDespiteTheCooldown() {
        at(0, left("chair", 2.4f))
        assertEquals(1, at(9, left("chair", 0.8f)).size) // about 2.6 feet: very close
    }

    @Test
    fun movingAwayIsNotSpoken() {
        at(0, left("chair", 0.8f))
        assertTrue(at(3, left("chair", 2.4f)).isEmpty())
    }

    @Test
    fun aHazardIsSaidAgainOnceTheRepeatWindowPasses() {
        // Present at every look, as a chair that stays put is.
        at(0, left("chair", 2.4f))
        (2L..20L step 2).forEach { assertTrue("at $it s", at(it, left("chair", 2.4f)).isEmpty()) }
        assertEquals(1, at(22, left("chair", 2.4f)).size)
    }

    @Test
    fun absentLongerThanForgetMsThenBackIsSpoken() {
        at(0, left("chair", 2.4f))
        assertTrue(at(3).isEmpty())
        assertTrue(at(6).isEmpty())
        assertEquals(1, at(7, left("chair", 2.4f)).size)
    }

    @Test
    fun absentBrieflyThenBackIsNotSpokenAgain() {
        at(0, left("chair", 2.4f))
        assertTrue(at(3).isEmpty())
        assertTrue(at(6, left("chair", 2.4f)).isEmpty())
    }

    @Test
    fun twoChairsOnTheSameSideAndBucketAreOneItem() {
        val said = at(0, left("chair", 2.4f), left("chair", 2.0f))
        assertEquals(1, said.size)
        assertEquals(2.0f, said.single().depth)
    }

    @Test
    fun veryCloseRepeatsOnlyAfterTheCriticalWindow() {
        assertEquals(1, at(0, left("chair", 0.8f)).size)
        assertTrue(at(3, left("chair", 0.8f)).isEmpty())
        assertTrue(at(6, left("chair", 0.8f)).isEmpty())
        assertEquals(1, at(7, left("chair", 0.8f)).size)
    }

    @Test
    fun theBriefsExampleRunsAsDescribed() {
        assertEquals(1, at(0, left("chair", 2.4f)).size)
        assertTrue(at(3, left("chair", 2.4f)).isEmpty())
        assertTrue(at(6, left("chair", 1.2f)).isEmpty())
        assertEquals(1, at(9, left("chair", 0.8f)).size)
        assertTrue(at(12, left("chair", 0.8f)).isEmpty())
        assertEquals(1, at(16, left("chair", 0.8f)).size)
    }

    @Test
    fun nothingNewIsAnEmptyList() {
        assertTrue(at(0).isEmpty())
    }

    @Test
    fun resetForgetsEverything() {
        at(0, left("chair", 2.4f))
        announcer.reset()
        assertEquals(1, at(1, left("chair", 2.4f)).size)
    }

    @Test
    fun bucketsFollowTheFeetBoundaries() {
        assertEquals(DistanceBucket.VERY_CLOSE, distanceBucketOf(0.9f)) // 2.95 ft
        assertEquals(DistanceBucket.CLOSE, distanceBucketOf(0.92f)) // 3.02 ft
        assertEquals(DistanceBucket.CLOSE, distanceBucketOf(2.43f)) // 7.97 ft
        assertEquals(DistanceBucket.AHEAD, distanceBucketOf(2.5f)) // 8.2 ft
    }
}
