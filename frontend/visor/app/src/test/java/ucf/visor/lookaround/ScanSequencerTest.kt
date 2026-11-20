package ucf.visor.lookaround

import com.meta.wearable.dat.motion.types.MotionSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ucf.visor.lookaround.ScanSequencer.Action
import ucf.visor.lookaround.ScanSequencer.Cue
import ucf.visor.motion.HeadingTracker
import ucf.visor.motionlab.SyntheticMotion
import ucf.visor.motionlab.SyntheticMotion.Leg

/**
 * One whole Look around, driven by synthetic head movement through the same
 * HeadingTracker the glasses' samples go through, on the samples' own clock:
 * which cues are said, where each picture is taken, and how a stop the head
 * never reaches, or a head that won't settle, is handled.
 */
class ScanSequencerTest {

    /** Runs a scan over [samples]; every picture is taken the moment it is asked for. */
    private fun scan(plan: ScanPlan, samples: List<MotionSample>): List<Action> {
        val sequencer = ScanSequencer(plan)
        val tracker = HeadingTracker()
        val actions = mutableListOf<Action>()
        for (sample in samples) {
            if (sequencer.isDone) break
            val g = sample.gyroscope!!
            val pose = tracker.update(sample.timestampNs, floatArrayOf(g.x, g.y, g.z))
            val now = sequencer.onPose(pose, sample.timestampNs / 1_000_000)
            actions += now
            if (now.any { it is Action.Capture }) actions += sequencer.onCaptureDone(taken = true)
        }
        return actions
    }

    private fun List<Action>.cues() = filterIsInstance<Action.Say>().map { it.cue }
    private fun List<Action>.captures() = filterIsInstance<Action.Capture>()

    @Test
    fun theStandardScanPicturesAheadLeftAndRightThenFacesAheadAgain() {
        // Ahead, 38° left, 38° right, ahead again.
        val standard = listOf(Leg(0.0, 1.0, 2.0), Leg(38.0, 1.5, 2.0), Leg(-38.0, 2.5, 2.0), Leg(0.0, 1.5, 4.0))
        val actions = scan(ScanPlan.STANDARD, SyntheticMotion.lookAround(durationSec = 60.0, legs = standard))

        assertEquals(listOf(Cue.HOLD_AHEAD, Cue.TURN_LEFT, Cue.TURN_RIGHT, Cue.FACE_AHEAD), actions.cues())
        val shots = actions.captures()
        assertEquals(listOf(0, 1, 2), shots.map { it.stop })
        // Each where the head actually held still: ahead, 38° left, 38° right.
        assertEquals(0.0, shots[0].yawDeg, 1.0)
        assertEquals(38.0, shots[1].yawDeg, 1.0)
        assertEquals(-38.0, shots[2].yawDeg, 1.0)
        assertTrue(actions.none { it is Action.Missed })
        val finished = actions.last() as Action.Finished
        assertEquals(0.0, finished.yawDeg, 10.0)
    }

    @Test
    fun theWideScanGoesFurtherEachWay() {
        val actions = scan(ScanPlan.WIDE, SyntheticMotion.lookAround(durationSec = 60.0))

        assertEquals(
            listOf(Cue.HOLD_AHEAD, Cue.TURN_LEFT, Cue.KEEP_LEFT, Cue.TURN_RIGHT, Cue.KEEP_RIGHT, Cue.FACE_AHEAD),
            actions.cues(),
        )
        assertEquals(listOf(0.0, 38.0, 72.0, -38.0, -72.0), actions.captures().map { Math.round(it.yawDeg).toDouble() })
        assertTrue(ScanPlan.WIDE.reachDeg > 85)
        assertTrue(ScanPlan.STANDARD.reachDeg > 50)
    }

    @Test
    fun aSideTheHeadNeverReachesIsGivenUpOnAndSaid() {
        // Turns left, then only back to ahead, and never to the right.
        val stiff = SyntheticMotion.lookAround(
            durationSec = 60.0,
            legs = listOf(Leg(0.0, 1.0, 2.0), Leg(38.0, 1.5, 2.0), Leg(0.0, 1.5, 60.0)),
        )
        val actions = scan(ScanPlan.STANDARD, stiff)

        assertEquals(listOf(0, 1), actions.captures().map { it.stop })
        val missed = actions.filterIsInstance<Action.Missed>().single()
        assertEquals(-ScanPlan.STOP_SPACING_DEG, missed.stopYawDeg, 0.0)
        assertTrue(actions.last() is Action.Finished)
        assertEquals("I couldn't get a picture of your right.", LookAroundPhrases.missed(listOf(missed.stopYawDeg)))
    }

    @Test
    fun aHeadThatCannotSettleHearsHoldStillAndStillGetsItsPicture() {
        val sequencer = ScanSequencer(ScanPlan.STANDARD)
        val actions = mutableListOf<Action>()
        // Ahead, but moving at 10 °/s throughout: over the lab's 6.4, under the relaxed 15.
        var t = 0L
        while (t <= 4_000 && actions.none { it is Action.Capture }) {
            actions += sequencer.onPose(HeadingTracker.Pose(0.0, 10.0, t * 1_000_000), t)
            t += 33
        }
        assertEquals(listOf(Cue.HOLD_AHEAD, Cue.HOLD_STILL), actions.cues())
        val capture = actions.captures().single()
        assertEquals(0, capture.stop)
        assertTrue("pictured soon after the relaxed threshold, at ${t}ms", t in ScanSequencer.RELAX_AFTER_MS..3_000)
    }

    @Test
    fun aPictureThatFailsIsAMissedStopAndTheScanCarriesOn() {
        val sequencer = ScanSequencer(ScanPlan.STANDARD)
        var t = 0L
        var asked = false
        while (!asked) {
            asked = sequencer.onPose(HeadingTracker.Pose(0.0, 1.0, t * 1_000_000), t).any { it is Action.Capture }
            t += 33
        }
        val after = sequencer.onCaptureDone(taken = false)
        assertEquals(Action.Missed(0, 0.0), after.first())
        assertEquals(Action.Say(Cue.TURN_LEFT), after.last())
        assertEquals(ScanPlan.STOP_SPACING_DEG, sequencer.target!!, 0.0)
    }
}
