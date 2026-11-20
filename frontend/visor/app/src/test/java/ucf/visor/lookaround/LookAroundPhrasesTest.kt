package ucf.visor.lookaround

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Look around says: from left to right, by clock hour, and never as if the room were empty. */
class LookAroundPhrasesTest {

    private fun sighting(name: String, bearing: Double, meters: Float) = Sighting(name, bearing, meters, 0.8f, 0.0, 0)

    private val room = listOf(
        sighting("person", -60.0, 2.8f),
        sighting("couch", 62.0, 1.8f),
        sighting("dining table", 2.0, 1.2f),
    )

    @Test
    fun foundThingsAreSaidFromLeftToRightByClockHour() {
        assertEquals(
            "From left to right: Couch at 10 o'clock, 6 feet away. " +
                "Dining table at 12 o'clock, 4 feet away. Person at 2 o'clock, 9 feet away.",
            LookAroundPhrases.found(room, facingYawDeg = 0.0),
        )
    }

    @Test
    fun theHoursAreFromWhereTheWearerFacesAtTheEnd() {
        // Ended the scan facing 30° left: the couch is nearer ahead, the person further right.
        val said = LookAroundPhrases.found(room, facingYawDeg = 30.0)
        assertTrue(said, said.contains("Couch at 11 o'clock"))
        assertTrue(said, said.contains("Person at 3 o'clock"))
    }

    @Test
    fun aBriefAnswerNamesTheNearestStillFromLeftToRight() {
        val said = LookAroundPhrases.found(room, facingYawDeg = 0.0, limit = 2)
        assertEquals("From left to right: Couch at 10 o'clock, 6 feet away. Dining table at 12 o'clock, 4 feet away.", said)
    }

    @Test
    fun sidesWithoutAPictureAreSaid() {
        assertTrue(LookAroundPhrases.found(room, 0.0, missedSides = listOf(35.0)).endsWith("I couldn't get a picture of your left."))
        assertTrue(LookAroundPhrases.found(room, 0.0, missedSides = listOf(35.0, -70.0)).endsWith("of your left or right."))
    }

    @Test
    fun nothingFoundDoesNotSoundLikeAnEmptyRoom() {
        assertEquals(LookAroundPhrases.NOTHING_RECOGNIZED, LookAroundPhrases.found(emptyList(), 0.0))
        assertFalse(LookAroundPhrases.NOTHING_RECOGNIZED.contains("clear"))
    }

    @Test
    fun theGuideHintNamesSomethingActuallyFound() {
        val said = LookAroundPhrases.found(room, 0.0, guideHint = true)
        assertTrue(said, said.endsWith("To find one, say VISOR GO, then guide me to the couch."))
    }

    @Test
    fun guidanceSaysWhichWayToTurnAndWhenItIsAhead() {
        val couch = room[1]
        assertEquals("Couch, at 10 o'clock. Turn your head slowly to the left.", LookAroundPhrases.guideStart(couch, 0.0))
        assertEquals("Person, at 2 o'clock. Turn your head slowly to the right.", LookAroundPhrases.guideStart(room[0], 0.0))
        assertEquals("Couch, straight ahead, 6 feet away.", LookAroundPhrases.guideStart(couch, 60.0))
        assertEquals("Couch, straight ahead, 6 feet away.", LookAroundPhrases.arrived(couch))
    }

    @Test
    fun askingForSomethingNotFoundSaysWhatWas() {
        assertEquals(
            "I didn't see a door when I looked around. I found a person, a couch and a dining table.",
            LookAroundPhrases.notFound("the door", room),
        )
    }
}
