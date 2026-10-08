package ucf.visor.motionlab.protocol

import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrialRunnerTest {

    private class FakeHost(var streamProblem: String? = null) : TrialHost {
        val streamsReady = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val marks = mutableListOf<Pair<String, Map<String, Any?>?>>()
        val markTimes = mutableListOf<Long>()
        var clock: () -> Long = { 0L }
        val ends = mutableListOf<Triple<TrialOutcome, String?, Boolean>>()

        override suspend fun awaitStreams(trial: Trial): String? {
            events += "await"
            streamsReady.await()
            return streamProblem
        }

        override fun beginRecording(trial: Trial) {
            events += "begin"
        }

        override fun mark(label: String, extra: Map<String, Any?>?) {
            marks += label to extra
            markTimes += clock()
        }

        override suspend fun endTrial(trial: Trial, outcome: TrialOutcome, reason: String?, recorded: Boolean) {
            ends += Triple(outcome, reason, recorded)
        }
    }

    private class RecordingFeedback : TrialFeedback {
        val countdowns = mutableListOf<Int>()
        val cues = mutableListOf<Pair<String, CueStyle>>()
        var finished: TrialOutcome? = null
        override fun countdown(trial: Trial, secondsLeft: Int) { countdowns += secondsLeft }
        override fun cue(trial: Trial, label: String, index: Int, style: CueStyle) { cues += label to style }
        override fun finished(trial: Trial, outcome: TrialOutcome, reason: String?) { finished = outcome }
    }

    private val trial = Trial(
        id = "T1", tier = "V", title = "Test", worn = true, prepSec = 3, durationSec = 10,
        purpose = "", setup = "", instructions = emptyList(),
        cue = Cue(intervalMs = 2000, steps = listOf("LEFT", "RIGHT")),
    )

    private fun TestScope.runner(host: FakeHost, feedback: RecordingFeedback) =
        TrialRunner(this, host, feedback, now = { testScheduler.currentTime })

    @Test
    fun countsDownWaitsForTheGlassesThenRecordsWithCues() = runTest {
        val host = FakeHost()
        val feedback = RecordingFeedback()
        val runner = runner(host, feedback)
        runner.start(trial)
        advanceTimeBy(3_500)
        assertEquals(listOf(3, 2, 1), feedback.countdowns)
        assertTrue(runner.state.value is RunnerState.WaitingForGlasses)
        assertFalse("nothing recorded before the streams are up", "begin" in host.events)

        // The glasses take a while; recording must start only once they are up.
        advanceTimeBy(4_000)
        host.streamsReady.complete(Unit)
        runCurrent()
        assertTrue("begin" in host.events)
        assertTrue(runner.state.value is RunnerState.Recording)

        advanceUntilIdle()
        // 10 s at one cue per 2 s: cues at 0, 2, 4, 6, 8 s.
        assertEquals(listOf("LEFT", "RIGHT", "LEFT", "RIGHT", "LEFT"), feedback.cues.map { it.first })
        assertEquals(listOf("trial_start", "cue", "cue", "cue", "cue", "cue", "trial_end"), host.marks.map { it.first })
        assertEquals(listOf(Triple(TrialOutcome.COMPLETED, null, true)), host.ends)
        assertEquals(TrialOutcome.COMPLETED, feedback.finished)
        val finished = runner.state.value as RunnerState.Finished
        assertTrue(finished.recorded)
        assertFalse(runner.isRunning)
    }

    @Test
    fun cuesStayOnScheduleRelativeToRecordingStart() = runTest {
        val host = FakeHost().also { it.streamsReady.complete(Unit) }
        val feedback = RecordingFeedback()
        val cueTimes = mutableListOf<Long>()
        val timedFeedback = object : TrialFeedback {
            override fun cue(trial: Trial, label: String, index: Int, style: CueStyle) {
                cueTimes += testScheduler.currentTime
            }
        }
        val runner = TrialRunner(this, host, timedFeedback, now = { testScheduler.currentTime })
        runner.start(trial.copy(prepSec = 0, cue = Cue(750, listOf("A", "B"), CueStyle.METRONOME)))
        advanceUntilIdle()
        assertEquals(14, cueTimes.size) // 0, 0.75, ... 9.75 s
        for ((i, t) in cueTimes.withIndex()) assertEquals(cueTimes[0] + i * 750L, t)
        assertTrue(feedback.cues.isEmpty())
    }

    @Test
    fun abortDuringCountdownEndsOnceWithoutRecording() = runTest {
        val host = FakeHost()
        val feedback = RecordingFeedback()
        val runner = runner(host, feedback)
        runner.start(trial)
        advanceTimeBy(1_500)
        runner.abort("tester stopped")
        runner.abort("pressed twice")
        advanceUntilIdle()
        assertEquals(listOf(Triple(TrialOutcome.ABORTED, "tester stopped", false)), host.ends)
        assertTrue(host.marks.isEmpty())
        assertFalse("begin" in host.events)
        assertEquals(TrialOutcome.ABORTED, feedback.finished)
    }

    @Test
    fun abortWhileRecordingKeepsTheDataAndMarksTheEnd() = runTest {
        val host = FakeHost().also { it.streamsReady.complete(Unit) }
        val runner = runner(host, RecordingFeedback())
        runner.start(trial.copy(prepSec = 0))
        advanceTimeBy(5_000)
        runner.abort()
        advanceUntilIdle()
        assertEquals(listOf(Triple(TrialOutcome.ABORTED, null, true)), host.ends)
        assertEquals("trial_end", host.marks.last().first)
        assertEquals("aborted", host.marks.last().second!!["outcome"])
    }

    @Test
    fun glassesThatNeverComeUpFailTheTrialWithTheirReason() = runTest {
        val host = FakeHost(streamProblem = "Camera permission was not granted").also { it.streamsReady.complete(Unit) }
        val feedback = RecordingFeedback()
        val runner = runner(host, feedback)
        runner.start(trial)
        advanceUntilIdle()
        assertEquals(listOf(Triple(TrialOutcome.FAILED, "Camera permission was not granted", false)), host.ends)
        assertEquals(TrialOutcome.FAILED, feedback.finished)
    }

    private val hold = trial.copy(id = "S1", tier = "S", prepSec = 2, durationSec = 20, cue = null, timedHold = true)

    @Test
    fun balanceLostEndsATimedHoldAsAResultWithItsLength() = runTest {
        val host = FakeHost().also { it.streamsReady.complete(Unit) }
        host.clock = { testScheduler.currentTime }
        val feedback = RecordingFeedback()
        val runner = runner(host, feedback)
        runner.start(hold)
        advanceTimeBy(9_500) // a 2 s countdown, then 7.5 s of the hold
        runner.balanceLost()
        runner.abort("pressed afterwards")
        advanceUntilIdle()
        assertEquals(listOf(Triple(TrialOutcome.BALANCE_LOST, "Balance lost", true)), host.ends)
        assertEquals(TrialOutcome.BALANCE_LOST, feedback.finished)
        assertEquals("balance_lost", host.marks.last().second!!["outcome"])
        val start = host.markTimes[host.marks.indexOfFirst { it.first == "trial_start" }]
        assertEquals(7_500L, host.markTimes.last() - start)
    }

    @Test
    fun balanceLostBeforeTheHoldStartsIsIgnored() = runTest {
        val host = FakeHost().also { it.streamsReady.complete(Unit) }
        val runner = runner(host, RecordingFeedback())
        runner.start(hold)
        advanceTimeBy(1_000) // still counting down
        runner.balanceLost()
        advanceUntilIdle()
        assertEquals(listOf(Triple(TrialOutcome.COMPLETED, null, true)), host.ends)
    }

    @Test
    fun jitteredCuesAreUnpredictableButStayInTheirWindows() = runTest {
        val host = FakeHost().also { it.streamsReady.complete(Unit) }
        var started = -1L
        val cueTimes = mutableListOf<Long>()
        val timed = object : TrialFeedback {
            override fun recordingStarted(trial: Trial) { started = testScheduler.currentTime }
            override fun cue(trial: Trial, label: String, index: Int, style: CueStyle) {
                cueTimes += testScheduler.currentTime
            }
        }
        val runner = TrialRunner(this, host, timed, Random(7), now = { testScheduler.currentTime })
        val impulses = Cue(3_000, listOf("IMPULSE"), CueStyle.TONE, jitterMs = 750, firstAtMs = 3_000, count = 10)
        runner.start(trial.copy(prepSec = 0, durationSec = 40, cue = impulses))
        advanceUntilIdle()
        assertEquals(10, cueTimes.size)
        assertEquals(3_000L, cueTimes.first() - started)
        val gaps = cueTimes.zipWithNext { a, b -> b - a }
        assertTrue("gaps $gaps", gaps.all { it in 2_250L..3_750L })
        assertTrue("not a fixed beat", gaps.toSet().size > 1)
        assertEquals(10, host.marks.count { it.first == "cue" })
    }

    @Test
    fun failMidRecordingIsReportedOnce() = runTest {
        val host = FakeHost().also { it.streamsReady.complete(Unit) }
        val runner = runner(host, RecordingFeedback())
        runner.start(trial.copy(prepSec = 0))
        advanceTimeBy(2_000)
        runner.fail("Glasses disconnected")
        runner.abort()
        advanceUntilIdle()
        assertEquals(listOf(Triple(TrialOutcome.FAILED, "Glasses disconnected", true)), host.ends)
    }
}
