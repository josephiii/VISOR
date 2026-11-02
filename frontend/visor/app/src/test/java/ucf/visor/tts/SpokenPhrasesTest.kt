package ucf.visor.tts

import org.junit.Assert.assertEquals
import org.junit.Test
import ucf.visor.network.Hazard

/** What a wearer hears from hazard detection: the nearest first, in whole feet. */
class SpokenPhrasesTest {

    private fun hazard(name: String, meters: Float) =
        Hazard(id = 0, coords = listOf(0f, 0f, 10f, 10f), depth = meters, name = name, conf = 0.9f)

    @Test
    fun hazardsAreReadNearestFirst() {
        val hazards = listOf(hazard("person", 2.4f), hazard("chair", 0.9f))

        assertEquals("chair, 3 feet ahead. person, 8 feet ahead.", SpokenPhrases.hazardsAhead(hazards))
    }

    @Test
    fun aCloseHazardIsOneFootNotZeroFeet() {
        assertEquals("cup, 1 foot ahead.", SpokenPhrases.hazardsAhead(listOf(hazard("cup", 0.05f))))
    }

    @Test
    fun noHazardsSaysSoWithoutSoundingLikeTheWayIsClear() {
        assertEquals(SpokenPhrases.NO_HAZARDS_FOUND, SpokenPhrases.hazardsAhead(emptyList()))
    }

    /** A hazard whose box spans [left] to [right] across a photo 300 pixels wide. */
    private fun hazardAt(name: String, meters: Float, left: Float, right: Float) =
        Hazard(id = 0, coords = listOf(left, 0f, right, 10f), depth = meters, name = name, conf = 0.9f)

    @Test
    fun eachHazardSaysWhichSideItIsOn() {
        val hazards = listOf(
            hazardAt("chair", 0.9f, left = 0f, right = 60f),
            hazardAt("person", 2.4f, left = 120f, right = 180f),
            hazardAt("bench", 3f, left = 250f, right = 300f),
        )

        assertEquals(
            "chair, 3 feet ahead on your left. person, 8 feet ahead. bench, 10 feet ahead on your right.",
            SpokenPhrases.hazardsAhead(hazards, imageWidth = 300),
        )
    }

    @Test
    fun aHazardAcrossTheMiddleIsAhead() {
        // Wider than the middle third, but centered in it.
        val table = hazardAt("dining table", 1.5f, left = 40f, right = 260f)
        assertEquals("dining table, 5 feet ahead.", SpokenPhrases.hazardsAhead(listOf(table), imageWidth = 300))
    }

    @Test
    fun withoutThePhotosWidthNoSideIsGuessed() {
        val chair = hazardAt("chair", 0.9f, left = 0f, right = 60f)
        assertEquals("chair, 3 feet ahead.", SpokenPhrases.hazardsAhead(listOf(chair)))
    }

    @Test
    fun aLimitKeepsTheNearest() {
        val hazards = listOf(
            hazard("person", 2.4f),
            hazard("chair", 0.9f),
            hazard("cup", 0.05f),
            hazard("bench", 3f),
        )
        assertEquals(
            "cup, 1 foot ahead. chair, 3 feet ahead. person, 8 feet ahead.",
            SpokenPhrases.hazardsAhead(hazards, limit = SpokenPhrases.BRIEF_HAZARD_LIMIT),
        )
    }
}
