package ucf.visor.tts

import org.junit.Assert.assertEquals
import org.junit.Test
import ucf.visor.network.Hazard
import ucf.visor.ui.viewmodel.SessionMode

/**
 * What a wearer hears from hazard detection, the nearest first, in whole feet;
 * and as a capture starts.
 */
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

    @Test
    fun aCaptureThatSwitchedModesSaysSoInTheSameSentence() {
        assertEquals(SpokenPhrases.TAKING_PICTURE, SpokenPhrases.takingPicture())
        assertEquals(
            "Switched to Reading Assistance. Taking picture",
            SpokenPhrases.takingPicture(switchedTo = SessionMode.READER),
        )
    }
}
