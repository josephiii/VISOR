package ucf.visor.lookaround

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ucf.visor.lookaround.GuideSequencer.Action
import ucf.visor.motion.HeadingTracker

/** "Guide me to the chair": leading the head round to face it, by tone and a few words. */
class GuideSequencerTest {

    private val chair = Sighting("chair", bearingDeg = 50.0, distanceM = 1.5f, confidence = 0.9f, offCentreDeg = 0.0, photo = 1)

    /** The head turning at [degPerSec] from [fromDeg] for [seconds], then holding; returns every action. */
    private fun follow(
        sequencer: GuideSequencer,
        path: (Double) -> Pair<Double, Double>,
        seconds: Double,
    ): List<Action> {
        val actions = mutableListOf<Action>()
        var t = 0.0
        while (t <= seconds && !sequencer.isDone) {
            val (yaw, speed) = path(t)
            actions += sequencer.onPose(HeadingTracker.Pose(yaw, speed, (t * 1e9).toLong()), (t * 1000).toLong())
            t += 1.0 / 30
        }
        return actions
    }

    @Test
    fun turningToItAndPausingArrives() {
        val sequencer = GuideSequencer(chair)
        // Turn left at 25 °/s to 50°, then hold.
        val actions = follow(sequencer, { t -> if (t < 2) 25 * t to 25.0 else 50.0 to 1.0 }, seconds = 4.0)
        assertEquals(listOf<Action>(Action.Arrived), actions)
        assertTrue(sequencer.isDone)
    }

    @Test
    fun swingingPastItSaysToComeBackOncePerPass() {
        val sequencer = GuideSequencer(chair)
        // Past the chair to 90°, a while there, then back past it to 10°.
        val actions = follow(
            sequencer,
            { t ->
                when {
                    t < 3 -> 30 * t to 30.0
                    t < 4 -> 90.0 to 30.0
                    t < 6 -> 90 - 40 * (t - 4) to 40.0
                    else -> 10.0 to 1.0
                }
            },
            seconds = 7.0,
        )
        assertEquals(
            listOf<Action>(Action.Overshot(toLeft = false), Action.Overshot(toLeft = true)),
            actions,
        )
    }

    @Test
    fun aHeadThatNeverGetsThereIsLetGo() {
        val sequencer = GuideSequencer(chair)
        val actions = follow(sequencer, { _ -> 0.0 to 0.0 }, seconds = 30.0)
        assertEquals(listOf<Action>(Action.TimedOut), actions)
    }

    @Test
    fun theToneQuickensAndRisesAsTheHeadGetsCloser() {
        assertEquals(GuideTone.SLOWEST_MS, GuideTone.intervalMs(120.0))
        assertEquals(GuideTone.FASTEST_MS, GuideTone.intervalMs(0.0))
        assertTrue(GuideTone.intervalMs(10.0) < GuideTone.intervalMs(40.0))
        assertTrue(GuideTone.pitchHz(10.0) > GuideTone.pitchHz(40.0))
        // The same either side: it says how far, and the words say which way.
        assertEquals(GuideTone.intervalMs(30.0), GuideTone.intervalMs(-30.0))
        assertEquals(0, GuideTone.pitchStep(200.0, 9))
        assertEquals(8, GuideTone.pitchStep(0.0, 9))
    }
}
