package ucf.visor.hazards

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ucf.visor.network.Hazard
import ucf.visor.tts.SpokenPhrases

@OptIn(ExperimentalCoroutinesApi::class)
class HazardWatchTest {

    private class FakeSpeaker : HazardWatchSpeaker {
        val said = mutableListOf<String>()
        var speaking = false
        override fun say(text: String) { said += text }
        override fun isSpeaking() = speaking
    }

    private val chair = Hazard(id = 0, coords = listOf(100f, 0f, 250f, 100f), depth = 2.4f, name = "chair", conf = 0.9f)

    private class Rig(
        val speaker: FakeSpeaker = FakeSpeaker(),
        var online: Boolean = true,
        var busy: Boolean = false,
        var captures: Int = 0,
        var picture: Int? = 1000,
        var answer: (suspend () -> List<Hazard>?) = { emptyList() },
    )

    private fun watch(rig: Rig, clockMs: () -> Long) = HazardWatch<Int>(
        speaker = rig.speaker,
        announcer = HazardAnnouncer(),
        capture = { rig.captures++; rig.picture },
        detect = { rig.answer() },
        widthOf = { it },
        isOnline = { rig.online },
        busy = { rig.busy },
        clockMs = clockMs,
    )

    @Test
    fun aNewHazardIsSpokenOnceAcrossTicks() = runTest {
        val rig = Rig(answer = { listOf(chair) })
        val job = launch { watch(rig) { currentTime }.run() }
        advanceTimeBy(HAZARD_WATCH_INTERVAL_MS * 3 + 1)
        job.cancel()
        assertEquals(listOf("chair, 8 feet ahead on your left."), rig.speaker.said)
        assertEquals(4, rig.captures)
    }

    @Test
    fun checksNeverOverlap() = runTest {
        var running = 0
        var most = 0
        val rig = Rig(answer = {
            running++
            most = maxOf(most, running)
            delay(HAZARD_WATCH_INTERVAL_MS * 2) // longer than a tick
            running--
            emptyList()
        })
        val job = launch { watch(rig) { currentTime }.run() }
        advanceTimeBy(HAZARD_WATCH_INTERVAL_MS * 10)
        job.cancel()
        assertEquals(1, most)
        assertTrue(rig.captures >= 2)
    }

    @Test
    fun skipsATickWhileSpeaking() = runTest {
        val rig = Rig()
        rig.speaker.speaking = true
        val job = launch { watch(rig) { currentTime }.run() }
        runCurrent()
        assertEquals(0, rig.captures)
        rig.speaker.speaking = false
        advanceTimeBy(HAZARD_WATCH_INTERVAL_MS + 1)
        job.cancel()
        assertEquals(1, rig.captures)
    }

    @Test
    fun skipsATickWhileAnotherFeatureIsInFlight() = runTest {
        val rig = Rig(busy = true)
        val job = launch { watch(rig) { currentTime }.run() }
        advanceTimeBy(HAZARD_WATCH_INTERVAL_MS * 2)
        assertEquals(0, rig.captures)
        rig.busy = false
        advanceTimeBy(HAZARD_WATCH_INTERVAL_MS)
        job.cancel()
        assertEquals(1, rig.captures)
    }

    @Test
    fun threeFailuresSayCantSeeOnceAndSuccessResetsTheCount() = runTest {
        val rig = Rig(answer = { null })
        val job = launch { watch(rig) { currentTime }.run() }
        advanceTimeBy(HAZARD_WATCH_INTERVAL_MS * 6)
        assertEquals(listOf(SpokenPhrases.HAZARD_WATCH_CANT_SEE), rig.speaker.said)

        rig.answer = { emptyList() } // one success: nothing said, count back to zero
        advanceTimeBy(HAZARD_WATCH_INTERVAL_MS)
        assertEquals(1, rig.speaker.said.size)

        rig.answer = { null }
        advanceTimeBy(HAZARD_WATCH_INTERVAL_MS * 3)
        job.cancel()
        assertEquals(2, rig.speaker.said.count { it == SpokenPhrases.HAZARD_WATCH_CANT_SEE })
    }

    @Test
    fun aPictureThatCouldNotBeTakenCountsAsAFailure() = runTest {
        val rig = Rig(picture = null)
        val job = launch { watch(rig) { currentTime }.run() }
        advanceTimeBy(HAZARD_WATCH_INTERVAL_MS * 2 + 1)
        job.cancel()
        assertEquals(listOf(SpokenPhrases.HAZARD_WATCH_CANT_SEE), rig.speaker.said)
    }

    @Test
    fun offlinePausesOnceAndBackIsSaidOnceOnResume() = runTest {
        val rig = Rig(online = false)
        val job = launch { watch(rig) { currentTime }.run() }
        advanceTimeBy(HAZARD_WATCH_INTERVAL_MS * 3)
        assertEquals(listOf(SpokenPhrases.HAZARD_WATCH_PAUSED_OFFLINE), rig.speaker.said)
        assertEquals(0, rig.captures)

        rig.online = true
        advanceTimeBy(HAZARD_WATCH_INTERVAL_MS * 2)
        job.cancel()
        assertEquals(
            listOf(SpokenPhrases.HAZARD_WATCH_PAUSED_OFFLINE, SpokenPhrases.HAZARD_WATCH_BACK),
            rig.speaker.said,
        )
        assertTrue(rig.captures > 0)
    }

    @Test
    fun cancellingStopsTheLoopEvenMidCheck() = runTest {
        val gate = CompletableDeferred<List<Hazard>?>()
        val rig = Rig(answer = { gate.await() })
        val job = launch { watch(rig) { currentTime }.run() }
        runCurrent()
        assertEquals(1, rig.captures)
        job.cancel()
        runCurrent()
        advanceTimeBy(HAZARD_WATCH_INTERVAL_MS * 5)
        assertTrue(job.isCancelled)
        assertEquals(1, rig.captures)
        assertFalse(rig.speaker.said.isNotEmpty())
    }
}
