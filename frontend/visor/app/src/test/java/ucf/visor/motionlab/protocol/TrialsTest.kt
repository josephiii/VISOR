package ucf.visor.motionlab.protocol

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The battery's shape is part of the protocol: the analysis groups recordings by
 * [Trial.condition], the tester relies on "Balance lost" being offered exactly
 * where a hold is the result, and every head impulse tone must fit its trial.
 */
class TrialsTest {

    private val battery = Trials.all.filter { it.tier == "S" }
    private val stances = battery.filter { it.condition["task"] in setOf("romberg", "tandem", "single_leg") }

    @Test
    fun idsAreUniqueAndEveryTierIsListed() {
        assertEquals(Trials.all.size, Trials.all.map { it.id }.toSet().size)
        assertTrue(Trials.all.all { it.tier in Trials.tiers })
        assertTrue(Trials.tiers.keys.all { tier -> Trials.all.any { it.tier == tier } })
    }

    @Test
    fun theBatteryOpensTheLabAndCoversEveryCondition() {
        assertEquals("S1_romberg_eyes_open", Trials.all.first().id)
        assertEquals(13, battery.size)
        val conditions = battery.map { it.condition }.toSet()
        for (eyes in listOf("open", "closed")) {
            assertTrue(mapOf("task" to "romberg", "eyes" to eyes) in conditions)
            for (front in listOf("left", "right")) {
                assertTrue(mapOf("task" to "tandem", "eyes" to eyes, "front" to front) in conditions)
            }
            for (leg in listOf("dominant", "non_dominant")) {
                assertTrue(mapOf("task" to "single_leg", "eyes" to eyes, "leg" to leg) in conditions)
            }
        }
        assertTrue(mapOf("task" to "head_impulse", "plane" to "horizontal") in conditions)
        for (plane in listOf("horizontal", "vertical")) {
            assertTrue(mapOf("task" to "gaze_stabilization", "plane" to plane) in conditions)
        }
    }

    @Test
    fun onlyTheStancesAreTimedHoldsAndTheyRunTheirStandardLengths() {
        assertEquals(10, stances.size)
        assertTrue(stances.all { it.timedHold && it.worn && !it.camera })
        assertTrue(battery.filter { it !in stances }.none { it.timedHold })
        assertTrue(Trials.all.filter { it.tier != "S" }.none { it.timedHold })
        for (t in stances) {
            assertEquals(t.id, if (t.condition["task"] == "single_leg") 20 else 30, t.durationSec)
        }
        assertTrue(battery.filter { it.condition["task"] == "gaze_stabilization" }.all { it.durationSec == 30 })
    }

    @Test
    fun eyesClosedTrialsSayWhenToCloseThem() {
        for (t in stances.filter { it.condition["eyes"] == "closed" }) {
            assertTrue(t.id, t.brief!!.contains("Close your eyes at the start tones"))
        }
        assertTrue(battery.all { it.brief != null })
    }

    @Test
    fun theHeadImpulseAndGazeTasksRecordTheCamera() {
        val camera = battery.filter { it.camera }.map { it.condition["task"] }
        assertEquals(listOf("head_impulse", "gaze_stabilization", "gaze_stabilization"), camera)
    }

    @Test
    fun everyHeadImpulseToneFitsItsTrialWhateverTheJitter() {
        val hit = Trials.byId("S4_head_impulse")!!
        val cue = hit.cue!!
        assertEquals(CueStyle.TONE, cue.style)
        assertEquals(10, cue.count)
        val durationMs = hit.durationSec * 1_000L
        // Worst case: every gap at its longest. The last tone still leaves time to turn.
        val latest = cue.firstAtMs + (cue.count!! - 1) * (cue.intervalMs + cue.jitterMs)
        assertTrue("last tone at $latest ms", latest + 2_000 < durationMs)
        repeat(500) { seed -> assertEquals(10, cue.times(durationMs, Random(seed)).size) }
    }

    @Test
    fun fixedCuesFallOnTheBeat() {
        assertEquals(listOf(0L, 2_000L, 4_000L, 6_000L, 8_000L), Cue(2_000, listOf("A", "B")).times(10_000))
    }
}
