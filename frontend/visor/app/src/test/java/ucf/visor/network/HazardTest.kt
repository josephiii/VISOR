package ucf.visor.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The app and the backend agree on the hazard detection answer only through
 * these field names, so a rename on either side should fail here first.
 */
class HazardTest {

    @Test
    fun theBackendsAnswerDecodes() {
        // As backend/routes/YoloRoutes.py sends it: YoloService's fields plus "depth".
        val answer = """
            [{"object": "chair", "classID": 56, "coords": [300.5, 250.0, 380.0, 420.25], "conf": 0.62, "depth": 0.9}]
        """

        val hazards = Json.decodeFromString<List<Hazard>>(answer)

        assertEquals(
            listOf(
                Hazard(
                    id = 56,
                    coords = listOf(300.5f, 250f, 380f, 420.25f),
                    depth = 0.9f,
                    name = "chair",
                    conf = 0.62f,
                ),
            ),
            hazards,
        )
    }
}
