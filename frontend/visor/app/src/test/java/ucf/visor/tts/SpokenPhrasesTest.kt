package ucf.visor.tts

import org.junit.Assert.assertEquals
import org.junit.Test
import ucf.visor.hazards.Alert
import ucf.visor.hazards.Band
import ucf.visor.hazards.Urgency
import ucf.visor.network.Hazard
import ucf.visor.hazards.Side as HazardSide

/** What a wearer hears from hazard detection: the nearest first, in whole feet. */
class SpokenPhrasesTest {

    @Test
    fun anOfflineSceneSaysItIsOnlyObjectsFirst() {
        assertEquals(
            "You're offline, so I can only name objects. I see cup, table.",
            SpokenPhrases.offlineScene(SpokenPhrases.objectsSeen(listOf("cup", "table"))),
        )
    }

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

    @Test
    fun aCriticalAlertStartsWithStop() {
        assertEquals(
            "Stop. Car ahead, very close.",
            SpokenPhrases.hazardAlert(Alert(Urgency.CRITICAL, "car", 1, HazardSide.AHEAD, Band.VERY_CLOSE)),
        )
    }

    @Test
    fun likeObjectsAreCountedInOneAlert() {
        assertEquals(
            "3 people on your left, close.",
            SpokenPhrases.hazardAlert(Alert(Urgency.WARNING, "person", 3, HazardSide.LEFT, Band.CLOSE)),
        )
        assertEquals(
            "2 benches on your right, ahead.",
            SpokenPhrases.hazardAlert(Alert(Urgency.NOTICE, "bench", 2, HazardSide.RIGHT, Band.AHEAD)),
        )
    }

    @Test
    fun aheadIsSaidOnce() {
        assertEquals(
            "Bench ahead.",
            SpokenPhrases.hazardAlert(Alert(Urgency.NOTICE, "bench", 1, HazardSide.AHEAD, Band.AHEAD)),
        )
    }

    @Test
    fun cantSeeSaysWhyWhenItKnows() {
        assertEquals(
            "Hazard watch can't see right now. VISOR can't reach its server. I'll tell you when it's back.",
            SpokenPhrases.hazardWatchCantSee(SpokenPhrases.HAZARD_WATCH_NO_SERVER),
        )
        assertEquals(SpokenPhrases.HAZARD_WATCH_CANT_SEE, SpokenPhrases.hazardWatchCantSee(null))
    }

    @Test
    fun theWatchSaysWhetherItIsOnAndCanSee() {
        assertEquals("Hazard watch is off.", SpokenPhrases.hazardWatchStatus(ucf.visor.hazards.HazardWatchState.OFF))
        assertEquals(
            "Hazard watch is on, but can't see right now.",
            SpokenPhrases.hazardWatchStatus(ucf.visor.hazards.HazardWatchState.CANT_SEE),
        )
    }

    @Test
    fun namesAlreadyPluralStayAsTheyAre() {
        assertEquals(
            "2 stairs ahead, close.",
            SpokenPhrases.hazardAlert(Alert(Urgency.WARNING, "stairs", 2, HazardSide.AHEAD, Band.CLOSE)),
        )
        assertEquals(
            "2 buses on your left, close.",
            SpokenPhrases.hazardAlert(Alert(Urgency.WARNING, "bus", 2, HazardSide.LEFT, Band.CLOSE)),
        )
    }
}
