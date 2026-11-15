package ucf.visor.hazards

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ucf.visor.network.Hazard

/** HazardWatch's loop on virtual time: what it says, when it says it can't see, and stopping. */
@OptIn(ExperimentalCoroutinesApi::class)
class HazardWatchLoopTest {

    private val said = mutableListOf<String>()

    private val chair = Hazard(id = 56, coords = listOf(280f, 200f, 360f, 400f), depth = 2f, name = "chair", conf = 0.8f)

    private fun TestScope.watch(config: HazardWatchConfig = HazardWatchConfig(), check: suspend () -> CheckedFrame?) =
        HazardWatch(
            scope = backgroundScope,
            check = check,
            onAlert = { said += "alert ${it.name}" },
            onCantSee = { said += "can't see" },
            onBack = { said += "back" },
            now = { testScheduler.currentTime },
            config = { config },
        )

    private fun TestScope.frame(vararg hazards: Hazard) =
        CheckedFrame(hazards.toList(), 640, 480, testScheduler.currentTime)

    @Test
    fun aChairInThePathIsSaidOnceThenRemindedWhileStillInThePath() = runTest {
        val watch = watch { frame(chair) }
        watch.start()
        advanceTimeBy(10_000)

        assertEquals(listOf("alert chair", "alert chair"), said) // once, then the 8 s reminder
        assertEquals(HazardWatchState.WATCHING, watch.state.value)
    }

    @Test
    fun startingUpGetsTenSecondsBeforeCantSee() = runTest {
        val watch = watch { null }
        watch.start()
        advanceTimeBy(9_000)
        assertTrue(said.isEmpty())

        advanceTimeBy(1_500)
        assertEquals(listOf("can't see"), said)
        assertEquals(HazardWatchState.CANT_SEE, watch.state.value)
    }

    @Test
    fun aDropoutAfterSeeingIsToldWithinThreeSecondsThenBack() = runTest {
        var failing = false
        val watch = watch { if (failing) null else frame() }
        watch.start()
        advanceTimeBy(2_000)
        failing = true
        advanceTimeBy(3_000) // last good check at 1.5 s; can't see from 4.5 s
        assertEquals(listOf("can't see"), said)

        failing = false
        advanceTimeBy(1_000)
        assertEquals(listOf("can't see", "back"), said)
        assertEquals(HazardWatchState.WATCHING, watch.state.value)
    }

    @Test
    fun cantSeeIsSaidAgainEveryMinuteItLasts() = runTest {
        val watch = watch { null }
        watch.start()
        advanceTimeBy(10_500 + 120_000)

        assertEquals(3, said.count { it == "can't see" })
    }

    @Test
    fun aCheckThatHangsCountsAsCantSeeAndIsNotDoubledUp() = runTest {
        var checks = 0
        val watch = watch {
            checks++
            awaitCancellation()
        }
        watch.start()
        // Each wait gives up after 3 s, so the 10 s start-up allowance runs out at the 12 s wait.
        advanceTimeBy(12_500)

        assertEquals(listOf("can't see"), said)
        assertEquals(1, checks)
    }

    @Test
    fun aLateAnswerFromAHungCheckIsNotSpoken() = runTest {
        // The chair is seen at 0 s, but the answer only arrives 4 s later.
        val watch = watch {
            val asked = testScheduler.currentTime
            kotlinx.coroutines.delay(4_000)
            CheckedFrame(listOf(chair), 640, 480, asked)
        }
        watch.start()
        advanceTimeBy(9_000)

        assertTrue(said.none { it.startsWith("alert") })
    }

    @Test
    fun stoppingSaysNothingMoreAndForgets() = runTest {
        val watch = watch { frame(chair) }
        watch.start()
        advanceTimeBy(1_000)
        watch.stop()
        val before = said.toList()
        advanceTimeBy(10_000)

        assertEquals(before, said)
        assertEquals(HazardWatchState.OFF, watch.state.value)
        assertFalse(watch.isOn)

        // Started again, the same chair is new: what was said before doesn't carry over.
        watch.start()
        advanceTimeBy(1_000)
        assertEquals(2, said.count { it == "alert chair" })
    }

    @Test
    fun sideNoticesAreSaidWhenTurnedOn() = runTest {
        val bench = Hazard(id = 13, coords = listOf(0f, 200f, 120f, 400f), depth = 2f, name = "bench", conf = 0.8f)
        val watch = watch(HazardWatchConfig(includeNotices = true)) { frame(bench) }
        watch.start()
        advanceTimeBy(2_000)

        assertEquals(listOf("alert bench"), said)
    }
}
