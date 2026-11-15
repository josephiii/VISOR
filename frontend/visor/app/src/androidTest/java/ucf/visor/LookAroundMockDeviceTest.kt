// LookAroundMockDeviceTest: Look around end to end, against MockDeviceKit.
//
// The real path on MWDAT 1.0: VISOR's held glasses session, its camera stream,
// and the Motion capability attached to that same session once the camera
// streams. Only the server is stood in for: each picture's objects come from a
// fake detector, so the test needs no network and knows the answer.

package ucf.visor

import android.Manifest
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.meta.wearable.dat.core.types.LinkState
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.GlassesModel
import com.meta.wearable.dat.mockdevice.api.MockGlasses
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ucf.visor.lookaround.GuideTonePlayer
import ucf.visor.lookaround.LookAround
import ucf.visor.lookaround.LookAroundPhrases
import ucf.visor.lookaround.ScanPlan
import ucf.visor.lookaround.ScanSequencer
import ucf.visor.motion.HeadMotion
import ucf.visor.motionlab.SyntheticMotion
import ucf.visor.network.Hazard
import ucf.visor.wearables.GlassesLease
import ucf.visor.wearables.HeldGlassesSession
import ucf.visor.wearables.preferredDevice
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
@LargeTest
class LookAroundMockDeviceTest {

    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
    )

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val motion = HeadMotion()
    private val video by lazy { File(context.cacheDir, "panning-look-around.mp4") }

    @After
    fun tearDown() {
        scope.cancel()
        motion.detach()
        VisorApp.from(context).engine.heldSession.unpin(LookAround.PIN_OWNER)
        MockDeviceKit.getInstance(context).disable()
        video.delete()
    }

    private fun pairWornGlasses(): MockGlasses {
        val kit = MockDeviceKit.getInstance(context)
        kit.enable()
        val glasses = kit.pairGlasses(GlassesModel.RAYBAN_META).getOrNull()
        assertNotNull("pairGlasses failed", glasses)
        glasses!!.powerOn()
        glasses.unfold()
        glasses.don()
        composeTestRule.waitUntil(15_000) { composeTestRule.activity.viewModel.uiState.value.canRegister }
        composeTestRule.waitUntil(15_000) {
            runCatching { preferredDevice()?.second?.linkState == LinkState.CONNECTED }.getOrDefault(false)
        }
        return glasses
    }

    @Test
    fun lookAroundPicturesEachStopAndGuidesTheHeadBackToWhatItFound() {
        PanningVideo.encode(video, pixelsPerFrame = 2)
        val glasses = pairWornGlasses()
        glasses.services.camera.setCameraFeed(Uri.fromFile(video))
        // A head holding still ahead, until the scan asks it to turn.
        val still = SyntheticMotion.lookAround(durationSec = 300.0, legs = listOf(SyntheticMotion.Leg(0.0, 1.0, 600.0)))
        glasses.services.motion.setMotionFeed(still, loop = true)

        val engine = VisorApp.from(context).engine
        val steps = CopyOnWriteArrayList<String>()
        val answers = CopyOnWriteArrayList<String>()
        val photoWidths = CopyOnWriteArrayList<Int>()
        lateinit var look: LookAround
        composeTestRule.runOnUiThread {
            look = LookAround(
                scope = scope,
                session = engine.heldSession,
                camera = engine.glassesCamera,
                motion = motion,
                // A chair in the middle of every picture, 1.5 m away.
                detect = { bitmap ->
                    photoWidths += bitmap.width
                    val w = bitmap.width.toFloat()
                    listOf(Hazard(id = 56, coords = listOf(w * 0.45f, 0f, w * 0.55f, 10f), depth = 1.5f, name = "chair", conf = 0.9f))
                },
                tones = GuideTonePlayer { 0f },
                say = object : LookAround.Say {
                    override fun step(text: String) {
                        steps += text
                    }

                    override fun answer(text: String) {
                        answers += text
                    }
                },
                plan = { ScanPlan.STANDARD },
                limit = { Int.MAX_VALUE },
                guideHint = { false },
                isSpeaking = { false },
                events = object : LookAround.Events {
                    override fun onScanStarted() = Unit
                    override fun onScanThinking() = Unit
                    override fun onAnswered(text: String) = Unit
                    override fun onGuideStarted() = Unit
                },
            )
            look.start()
        }

        // The ahead picture is taken while the head holds still; then the
        // head does what it is asked: left, right, ahead again.
        val turnLeft = LookAroundPhrases.cue(ScanSequencer.Cue.TURN_LEFT)
        composeTestRule.waitUntil(90_000) { turnLeft in steps || answers.isNotEmpty() }
        assertTrue("no turn-left cue; said: $steps, answered: $answers", turnLeft in steps)
        glasses.services.motion.setMotionFeed(SyntheticMotion.lookAround(durationSec = 300.0), loop = true)

        composeTestRule.waitUntil(120_000) { answers.isNotEmpty() }
        val answer = answers.single()
        assertEquals("one picture per stop: $steps", 3, photoWidths.size)
        // While Motion streamed, the session held the glasses throughout.
        assertEquals(HeldGlassesSession.LEASE_OWNER, GlassesLease.holder.value)
        assertEquals(HeadMotion.Status.RUNNING, motion.status.value)

        val found = look.found.value
        assertNotNull("nothing kept to guide to; answered: $answer", found)
        val bearings = found!!.sightings.map { it.bearingDeg }.sorted()
        assertEquals("three chairs, one per picture: $bearings", 3, bearings.size)
        // Pictured where the head held still: about 38° right, ahead, 38° left.
        assertEquals(-38.0, bearings[0], 8.0)
        assertEquals(0.0, bearings[1], 8.0)
        assertEquals(38.0, bearings[2], 8.0)
        assertTrue(answer, answer.startsWith("From left to right: Chair at "))

        // "Guide me to the chair on the left": the looping head passes it, and holds there.
        composeTestRule.runOnUiThread { look.guideTo(found.sightings.maxBy { it.bearingDeg }) }
        composeTestRule.waitUntil(60_000) { answers.size == 2 }
        assertEquals("Chair, straight ahead, 5 feet away.", answers[1])
    }
}
