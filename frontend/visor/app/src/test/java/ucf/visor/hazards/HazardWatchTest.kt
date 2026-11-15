package ucf.visor.hazards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ucf.visor.network.Hazard

/**
 * HazardTracker, HazardScorer and AlertPolicy together, on scripted frames:
 * what the hazard watch says, and above all what it doesn't say again.
 */
class HazardWatchTest {

    private val tracker = HazardTracker()
    private val policy = AlertPolicy()

    /** A 640 x 480 frame. In the path: centered, reaching the lower half. */
    private fun hazard(name: String, depth: Float, x1: Float = 280f, x2: Float = 360f) =
        Hazard(id = 0, coords = listOf(x1, 200f, x2, 400f), depth = depth, name = name, conf = 0.8f)

    /** Feeds one frame taken at [timeMs] and returns what would be said. */
    private fun frame(timeMs: Long, vararg hazards: Hazard): Alert? =
        policy.next(tracker.update(hazards.toList(), 640, 480, timeMs), nowMs = timeMs)

    /** Feeds frames every 500 ms, one per item, and returns every alert said. */
    private fun run(frames: List<List<Hazard>>, startMs: Long = 0): List<Alert> =
        frames.mapIndexedNotNull { i, hazards -> frame(startMs + i * 500L, *hazards.toTypedArray()) }

    @Test
    fun anObjectThatStaysPutIsSaidOnce() {
        val alerts = run(List(12) { listOf(hazard("chair", 2f)) }) // 6 s

        assertEquals(listOf(Alert(Urgency.WARNING, "chair", 1, Side.AHEAD, Band.CLOSE)), alerts)
    }

    @Test
    fun aWarningStillInThePathIsSaidOnceMoreAfterEightSeconds() {
        val alerts = run(List(20) { listOf(hazard("chair", 2f)) }) // 10 s

        assertEquals(2, alerts.size)
    }

    @Test
    fun aOneFrameDetectionIsNeverSaid() {
        val alerts = run(listOf(listOf(hazard("chair", 2f)), emptyList(), emptyList(), emptyList()))

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun anObjectThatFlickersIsNotRepeated() {
        val alerts = run(List(12) { i -> if (i % 3 == 2) emptyList() else listOf(hazard("chair", 2f)) })

        assertEquals(1, alerts.size)
    }

    @Test
    fun anApproachingObjectIsSaidOncePerBand() {
        val depths = listOf(3.5f, 3.2f, 2.9f, 2.6f, 2.3f, 2.0f, 1.7f, 1.4f, 1.1f, 0.9f, 0.7f, 0.5f, 0.5f)
        val alerts = run(depths.map { listOf(hazard("chair", it)) })

        assertEquals(Urgency.WARNING, alerts.first().urgency)
        assertEquals(1, alerts.count { it.urgency == Urgency.WARNING })
        assertEquals(Alert(Urgency.CRITICAL, "chair", 1, Side.AHEAD, Band.VERY_CLOSE), alerts[1])
    }

    @Test
    fun aCriticalHazardRepeatsAtMostThreeTimes() {
        val alerts = run(List(24) { listOf(hazard("chair", 0.5f)) }) // 12 s

        assertEquals(3, alerts.size)
        assertTrue(alerts.all { it.urgency == Urgency.CRITICAL })
    }

    @Test
    fun likeObjectsAppearingTogetherAreGrouped() {
        val people = { d: Float ->
            listOf(hazard("person", d, 220f, 270f), hazard("person", d, 295f, 345f), hazard("person", d, 370f, 420f))
        }
        val alerts = run(List(4) { people(2f) })

        assertEquals(listOf(Alert(Urgency.WARNING, "person", 3, Side.AHEAD, Band.CLOSE)), alerts)
    }

    @Test
    fun anObjectThatLeavesAndComesBackIsSaidAgain() {
        val here = List(4) { listOf(hazard("chair", 2f)) }
        val gone = List(6) { emptyList<Hazard>() } // 3 s, longer than dropAfterMs
        val alerts = run(here + gone + here)

        assertEquals(2, alerts.size)
    }

    @Test
    fun anObjectBrieflyHiddenIsNotSaidAgain() {
        val here = List(4) { listOf(hazard("chair", 2f)) }
        val hidden = List(2) { emptyList<Hazard>() } // 1 s, shorter than dropAfterMs
        val alerts = run(here + hidden + here)

        assertEquals(1, alerts.size)
    }

    @Test
    fun hoveringOnABandEdgeSaysNothingMore() {
        val depths = listOf(1.2f, 1.2f) + List(12) { i -> if (i % 2 == 0) 0.95f else 1.05f }
        val alerts = run(depths.map { listOf(hazard("chair", it)) })

        assertEquals(1, alerts.size)
    }

    @Test
    fun movingAwaySaysNothing() {
        val depths = listOf(1.5f, 1.5f, 2f, 2.5f, 3f, 3.5f)
        val alerts = run(depths.map { listOf(hazard("chair", it)) })

        assertEquals(1, alerts.size)
    }

    @Test
    fun aSecondObjectWaitsForTheGap() {
        val chair = hazard("chair", 2f)
        val person = hazard("person", 2f, 300f, 340f).copy(coords = listOf(300f, 0f, 340f, 400f))
        val alerts = (0..5).map { i -> i * 500L to frame(i * 500L, chair, person) }.filter { it.second != null }

        assertEquals(2, alerts.size)
        assertTrue(alerts[1].first - alerts[0].first >= 1_500)
    }

    @Test
    fun aLateAnswerIsNotSpoken() {
        tracker.update(listOf(hazard("chair", 2f)), 640, 480, 0)
        val tracks = tracker.update(listOf(hazard("chair", 2f)), 640, 480, 500)

        assertNull(policy.next(tracks, nowMs = 2_000, frameTimeMs = 500))
        // ...and is still said on the next fresh frame.
        assertEquals(Urgency.WARNING, policy.next(tracker.update(listOf(hazard("chair", 2f)), 640, 480, 2_100), 2_100)?.urgency)
    }

    @Test
    fun somethingOffToTheSideIsANoticeAndOffByDefault() {
        val alerts = run(List(4) { listOf(hazard("bench", 2f, 0f, 120f)) })

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun aPersonApproachingFromTheSideIsAWarning() {
        val depths = listOf(3.8f, 3.4f, 3.0f, 2.6f)
        val alerts = run(depths.map { listOf(hazard("person", it, 0f, 120f)) })

        assertEquals(listOf(Alert(Urgency.WARNING, "person", 1, Side.LEFT, Band.AHEAD)), alerts)
    }

    @Test
    fun aFarCarClosingInFastIsCritical() {
        // Beyond the 4 m bands, but its box doubles every second: contact in about a second.
        val heights = listOf(40f, 60f, 90f, 135f)
        val alerts = heights.mapIndexedNotNull { i, h ->
            val car = Hazard(id = 2, coords = listOf(500f, 300f - h, 560f, 300f), depth = 10f, name = "car", conf = 0.8f)
            frame(i * 500L, car)
        }

        assertEquals(Urgency.CRITICAL, alerts.first().urgency)
        assertEquals("car", alerts.first().name)
    }

    @Test
    fun aFarParkedCarSaysNothing() {
        val car = Hazard(id = 2, coords = listOf(500f, 200f, 560f, 300f), depth = 10f, name = "car", conf = 0.8f)
        val alerts = run(List(6) { listOf(car) })

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun stairsInThePathAreCriticalFromTwoMeters() {
        val alerts = run(List(3) { listOf(hazard("stairs", 1.8f)) })

        assertEquals(Alert(Urgency.CRITICAL, "stairs", 1, Side.AHEAD, Band.CLOSE), alerts.first())
    }
}
