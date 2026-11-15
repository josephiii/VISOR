package ucf.visor.lookaround

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ucf.visor.network.Hazard

/** Where in the room each found object is, said by clock hour, and found again by name. */
class SceneMapTest {

    private fun hazard(name: String, x1: Float, x2: Float, depth: Float) =
        Hazard(id = 0, coords = listOf(x1, 0f, x2, 100f), depth = depth, name = name, conf = 0.8f)

    private fun sighting(name: String, bearing: Double, meters: Float, photo: Int = 0, offCentre: Double = 0.0) =
        Sighting(name, bearing, meters, 0.8f, offCentre, photo)

    @Test
    fun theCameraSpansWhatTheLabMeasured() {
        // 8.02 px/° at a 360-pixel width (lab V4) is a 42.8° wide view.
        assertEquals(42.8, CameraOptics.HORIZONTAL_FOV_DEG, 0.1)
        assertEquals(0.0, CameraOptics.bearingInImage(360.0, 720), 1e-9)
        // The left edge is half the view to the left (positive), the right edge to the right.
        assertEquals(21.4, CameraOptics.bearingInImage(0.0, 720), 0.1)
        assertEquals(-21.4, CameraOptics.bearingInImage(720.0, 720), 0.1)
        // Same view at any stream size (Meta: scaled, not cropped).
        assertEquals(CameraOptics.bearingInImage(90.0, 360), CameraOptics.bearingInImage(180.0, 720), 1e-9)
    }

    @Test
    fun anObjectsDirectionIsTheHeadsPlusItsPlaceInThePhoto() {
        // A chair in the left quarter of a photo taken with the head 35° left.
        val chair = SceneMap.sightings(listOf(hazard("Chair", 100f, 260f, 1.2f)), 720, 35.0, photo = 1).single()
        assertEquals("chair", chair.name)
        assertEquals(35.0 + CameraOptics.bearingInImage(180.0, 720), chair.bearingDeg, 1e-9)
        assertEquals(1, chair.photo)
        // Boxes the detector couldn't place are left out, not guessed.
        assertEquals(emptyList<Sighting>(), SceneMap.sightings(listOf(hazard("cup", 0f, 0f, 1f).copy(coords = listOf(1f))), 720, 0.0, 0))
    }

    @Test
    fun theSameObjectInTwoOverlappingPhotosIsKeptOnceNearerItsMiddle() {
        val seenTwice = listOf(
            sighting("chair", 18.0, 2.0f, photo = 0, offCentre = 18.0),
            sighting("chair", 19.5, 2.2f, photo = 1, offCentre = 15.5),
        )
        val merged = SceneMap.merge(seenTwice).single()
        assertEquals(1, merged.photo)

        // Two chairs side by side in one photo are two chairs.
        val twoChairs = listOf(sighting("chair", 5.0, 2.0f, photo = 0), sighting("chair", 9.0, 2.1f, photo = 0))
        assertEquals(2, SceneMap.merge(twoChairs).size)
        // ... as are a near and a far one in the same direction, or two different things.
        assertEquals(2, SceneMap.merge(listOf(sighting("chair", 5.0, 1.0f, 0), sighting("chair", 6.0, 4.0f, 1))).size)
        assertEquals(2, SceneMap.merge(listOf(sighting("chair", 5.0, 1.0f, 0), sighting("person", 6.0, 1.0f, 1))).size)
    }

    @Test
    fun directionsAreClockHours() {
        assertEquals(12, SceneMap.clockHour(0.0))
        assertEquals(12, SceneMap.clockHour(14.0))
        assertEquals(11, SceneMap.clockHour(20.0))
        assertEquals(10, SceneMap.clockHour(60.0))
        assertEquals(9, SceneMap.clockHour(90.0))
        assertEquals(2, SceneMap.clockHour(-60.0))
        assertEquals(3, SceneMap.clockHour(-90.0))
        assertEquals(6, SceneMap.clockHour(180.0))
        // Relative to where the head faces now, not where the scan began.
        assertEquals(12, SceneMap.clockHour(sighting("door", 40.0, 3f).relativeTo(40.0)))
        assertEquals(2, SceneMap.clockHour(sighting("door", -20.0, 3f).relativeTo(40.0)))
    }

    @Test
    fun whatTheWearerAsksForIsFoundByWhatTheyCallIt() {
        val found = listOf(
            sighting("dining table", 0.0, 2f),
            sighting("chair", 30.0, 3f),
            sighting("chair", -30.0, 1.5f),
            sighting("person", 60.0, 4f),
            sighting("potted plant", -60.0, 2f),
        )
        assertEquals(-30.0, SceneMap.find(found, "the chair")!!.bearingDeg, 0.0) // the nearer chair
        assertEquals("chair", SceneMap.find(found, "chairs")!!.name)
        assertEquals("dining table", SceneMap.find(found, "the table")!!.name)
        assertEquals("dining table", SceneMap.find(found, "Dining Table?")!!.name)
        assertEquals("potted plant", SceneMap.find(found, "my plant")!!.name)
        assertEquals("person", SceneMap.find(found, "someone")!!.name)
        assertEquals("person", SceneMap.find(found, "people")!!.name)
        assertNull(SceneMap.find(found, "the door"))
        assertNull(SceneMap.find(found, "the"))
    }
}
