// MwdatMockDeviceTest — MWDAT 1.0 features end to end, against MockDeviceKit.
//
// No glasses needed: MockDeviceKit stands in for the whole SDK stack (registration,
// device link, capabilities), through the same code paths real glasses use.
//
// Scenarios:
// 1. Device state (1.0 `Device` fields) reaches VISOR's glasses status.
// 2. The head-motion lab records a synthetic IMU feed through the Motion capability
//    and writes a readable visor.imu.session/2 file.
// 3. "Hey Meta, start VISOR" (voice invocation) starts a session and is answered.
// 4. Tier S: "Balance lost" ends a timed hold as a result; head impulses are counted.

package ucf.visor

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.LinkState
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.GlassesModel
import com.meta.wearable.dat.mockdevice.api.MockGlasses
import java.util.zip.GZIPInputStream
import kotlin.math.PI
import kotlin.math.abs
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ucf.visor.motionlab.MotionLabViewModel
import ucf.visor.motionlab.SyntheticMotion
import ucf.visor.motionlab.protocol.Cue
import ucf.visor.motionlab.protocol.CueStyle
import ucf.visor.motionlab.protocol.RunnerState
import ucf.visor.motionlab.protocol.Trial
import ucf.visor.motionlab.protocol.TrialOutcome
import ucf.visor.motionlab.protocol.Trials
import ucf.visor.wearables.VoiceLaunches
import ucf.visor.wearables.preferredDevice
import ucf.visor.wearables.toGlassesStatus

@RunWith(AndroidJUnit4::class)
@LargeTest
class MwdatMockDeviceTest {

    // Granted before the activity starts, so VISOR's own permission request
    // resolves without a dialog and the SDK initializes straight away.
    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
    )

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        MockDeviceKit.getInstance(context).disable()
    }

    /** Enables the mock kit (registered by default) and pairs connected, worn glasses. */
    private fun pairWornGlasses(model: GlassesModel = GlassesModel.RAYBAN_META): MockGlasses {
        val kit = MockDeviceKit.getInstance(context)
        kit.enable()
        val glasses = kit.pairGlasses(model).getOrNull()
        assertNotNull("pairGlasses($model) failed", glasses)
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
    fun deviceStateReachesTheGlassesStatus() {
        val glasses = pairWornGlasses()
        glasses.setBatteryLevel(42)
        composeTestRule.waitUntil(10_000) {
            Wearables.devicesMetadata[glasses.deviceIdentifier]?.value?.batteryLevel == 42
        }
        val status = Wearables.devicesMetadata[glasses.deviceIdentifier]!!.value.toGlassesStatus()
        assertEquals(42, status.batteryPercent)
        assertTrue(status.connected)
        assertTrue(status.spokenSummary(), status.spokenSummary().contains("Battery 42 percent"))
    }

    @Test
    fun motionLabRecordsSyntheticImuThroughMwdatMotion() {
        val glasses = pairWornGlasses()
        // A looped 1 Hz, ±15° head shake on the mock IMU (MockMotionKit, new in 1.0).
        glasses.services.motion.setMotionFeed(SyntheticMotion.yawOscillation(), loop = true)

        val activity = composeTestRule.activity
        lateinit var lab: MotionLabViewModel
        composeTestRule.runOnUiThread {
            lab = ViewModelProvider(activity)[MotionLabViewModel::class.java]
            lab.observeGlasses()
            lab.setParticipant("instrumentation")
            lab.startTrial(SHORT_TRIAL) { PermissionStatus.Granted }
        }
        composeTestRule.waitUntil(90_000) { lab.uiState.value.runner is RunnerState.Finished }

        val result = lab.uiState.value.lastResult
        assertNotNull(result)
        assertEquals("reason: ${result!!.reason}", TrialOutcome.COMPLETED, result.outcome)
        val file = result.file
        assertNotNull("recording not saved", file)
        val json = JSONObject(GZIPInputStream(file!!.inputStream()).bufferedReader().readText())
        assertEquals("visor.imu.session/2", json.getString("schema"))
        assertEquals("instrumentation", json.getJSONObject("meta").getString("participant"))

        val motion = json.getJSONObject("streams").getJSONObject("dat_motion")
        val samples = motion.getInt("n")
        // Five seconds at 60 Hz; replay follows the configured rate.
        assertTrue("only $samples samples", samples >= 200)
        val gy = motion.getJSONObject("columns").getJSONArray("gy")
        var peak = 0.0
        for (i in 0 until gy.length()) if (!gy.isNull(i)) peak = maxOf(peak, abs(gy.getDouble(i)))
        // A 15° amplitude at 1 Hz peaks at 15° x 2π rad/s ≈ 1.645 rad/s.
        val expected = 15 * PI / 180 * 2 * PI
        assertTrue("peak yaw rate $peak", abs(peak - expected) < 0.2 * expected)
        val marks = json.getJSONArray("marks")
        val labels = (0 until marks.length()).map { marks.getJSONObject(it).getString("label") }
        assertTrue(labels.toString(), "trial_start" in labels && "trial_end" in labels)
        if (!keepRecordings) file.delete()
    }

    /**
     * The camera half of the lab, end to end: a video whose scene pans a known
     * 6 px per frame is played through MockDeviceKit's camera, and the lab's
     * phase correlation — running on the SDK's own decoded I420 frames — must
     * measure that shift, with the right sign.
     */
    @Test
    fun motionLabMeasuresImageShiftThroughTheMockCamera() {
        val video = java.io.File(context.cacheDir, "panning.mp4")
        val mime = PanningVideo.encode(video, pixelsPerFrame = 6)
        val glasses = pairWornGlasses()
        glasses.services.camera.setCameraFeed(android.net.Uri.fromFile(video))
        glasses.services.motion.setMotionFeed(SyntheticMotion.yawOscillation(), loop = true)

        val activity = composeTestRule.activity
        lateinit var lab: MotionLabViewModel
        composeTestRule.runOnUiThread {
            lab = ViewModelProvider(activity)[MotionLabViewModel::class.java]
            lab.observeGlasses()
            lab.setParticipant("instrumentation")
            lab.startTrial(SHORT_TRIAL.copy(id = "T0_instrumentation_camera", camera = true)) {
                PermissionStatus.Granted
            }
        }
        composeTestRule.waitUntil(120_000) { lab.uiState.value.runner is RunnerState.Finished }

        val result = lab.uiState.value.lastResult!!
        assertEquals("reason: ${result.reason} ($mime)", TrialOutcome.COMPLETED, result.outcome)
        val json = JSONObject(GZIPInputStream(result.file!!.inputStream()).bufferedReader().readText())
        val videoStream = json.getJSONObject("streams").getJSONObject("dat_video")
        val columns = videoStream.getJSONObject("columns")
        val frames = videoStream.getInt("n")
        assertTrue("only $frames frames", frames >= 60)

        // The scene moves left: -6 px per frame at the video's own size.
        val width = columns.getJSONArray("width").getDouble(0)
        val expected = -6.0 * width / 360.0
        val shifts = mutableListOf<Double>()
        val peaks = mutableListOf<Double>()
        val shiftX = columns.getJSONArray("shiftX")
        val refIndex = columns.getJSONArray("refIndex")
        for (i in 0 until frames) {
            if (shiftX.isNull(i) || refIndex.isNull(i)) continue
            // Frames skipped under load are compared across the gap.
            val gap = i - refIndex.getInt(i)
            shifts += shiftX.getDouble(i) / gap
            peaks += columns.getJSONArray("peak").getDouble(i)
        }
        assertTrue("only ${shifts.size} analyzed frames", shifts.size >= 30)
        val median = shifts.sorted()[shifts.size / 2]
        // The feed loops: the one frame where it jumps back is an outlier the median ignores.
        assertTrue("median shift $median px/frame, expected $expected", abs(median - expected) < 0.75)
        assertTrue("median peak ${peaks.sorted()[peaks.size / 2]}", peaks.sorted()[peaks.size / 2] > 0.2)
        if (!keepRecordings) result.file.delete()
        video.delete()
    }

    /** `-e keepRecordings true` leaves the session files on the device to adb pull. */
    private val keepRecordings: Boolean
        get() = InstrumentationRegistry.getArguments().getString("keepRecordings") == "true"

    private fun startLabTrial(trial: Trial): MotionLabViewModel {
        lateinit var lab: MotionLabViewModel
        composeTestRule.runOnUiThread {
            lab = ViewModelProvider(composeTestRule.activity)[MotionLabViewModel::class.java]
            lab.observeGlasses()
            lab.setParticipant("instrumentation")
            lab.startTrial(trial) { PermissionStatus.Granted }
        }
        return lab
    }

    private fun readRecording(file: java.io.File?): JSONObject {
        assertNotNull("recording not saved", file)
        return JSONObject(GZIPInputStream(file!!.inputStream()).bufferedReader().readText())
    }

    /**
     * Tier S, a timed hold: the tester's "Balance lost" ends the Romberg as a
     * result rather than an abort, the recording carries its length and
     * condition, and the battery moves on to the next task.
     */
    @Test
    fun balanceLostEndsARombergHoldAndTheBatteryMovesOn() {
        val glasses = pairWornGlasses()
        glasses.services.motion.setMotionFeed(
            SyntheticMotion.yawOscillation(durationSec = 60.0, frequencyHz = 0.5, amplitudeDeg = 0.5), loop = true)
        val romberg = Trials.byId("S1_romberg_eyes_open")!!.copy(prepSec = 1)
        val lab = startLabTrial(romberg)
        // Long enough to leave a sway window (2 s in, 1 s before the press, 5 s minimum).
        composeTestRule.waitUntil(60_000) {
            (lab.uiState.value.runner as? RunnerState.Recording)?.let { it.elapsedMs >= 9_000 } == true
        }
        composeTestRule.runOnUiThread { lab.balanceLost() }
        composeTestRule.waitUntil(30_000) { lab.uiState.value.runner is RunnerState.Finished }

        val result = lab.uiState.value.lastResult!!
        assertEquals("reason: ${result.reason}", TrialOutcome.BALANCE_LOST, result.outcome)
        val meta = readRecording(result.file).getJSONObject("meta")
        assertEquals("balance_lost", meta.getString("outcome"))
        val hold = meta.getDouble("holdSec")
        assertTrue("hold $hold s", hold in 9.0..15.0)
        assertTrue(meta.getBoolean("timedHold"))
        assertEquals("romberg", meta.getJSONObject("condition").getString("task"))
        assertEquals("open", meta.getJSONObject("condition").getString("eyes"))
        val balance = result.quickLook!!.balance!!
        assertTrue(balance.lost)
        assertNotNull(balance.headSpeedRmsDps)
        assertTrue(result.summary, result.summary.contains("The hold lasted"))
        assertTrue(result.summary, result.summary.contains("Next: Romberg, eyes closed"))
        assertEquals("S1_romberg_eyes_closed", lab.uiState.value.selectedTrialId)
        if (!keepRecordings) result.file!!.delete()
    }

    /**
     * Tier S, the head impulse test: impulses streamed through MWDAT Motion are
     * matched to the lab's tones and counted, with their peak speed, by the
     * same rule the analysis uses.
     */
    @Test
    fun headImpulsesAreCountedThroughMwdatMotion() {
        val glasses = pairWornGlasses()
        // One quick 15° turn a second, alternately left and right, peaking at
        // 187.5 °/s; long enough not to loop (and so reset its clock) mid-trial.
        glasses.services.motion.setMotionFeed(SyntheticMotion.headImpulses(durationSec = 60.0), loop = true)
        val trial = Trials.byId("S4_head_impulse")!!.copy(
            id = "T0_instrumentation_impulses",
            prepSec = 1,
            durationSec = 14,
            camera = false,
            cue = Cue(3_000, listOf("IMPULSE"), CueStyle.TONE, firstAtMs = 1_000, count = 4),
        )
        val lab = startLabTrial(trial)
        composeTestRule.waitUntil(90_000) { lab.uiState.value.runner is RunnerState.Finished }

        val result = lab.uiState.value.lastResult!!
        assertEquals("reason: ${result.reason}", TrialOutcome.COMPLETED, result.outcome)
        val impulses = result.quickLook!!.impulses!!
        assertEquals(4, impulses.tones)
        assertEquals(result.summary, 4, impulses.detected)
        assertEquals(0, impulses.slow)
        assertEquals(187.5, impulses.medianPeakDps!!, 187.5 * 0.1)
        assertTrue(result.summary, result.summary.contains("Found 4 impulses for 4 tones"))
        val cues = readRecording(result.file).getJSONObject("meta").getJSONObject("cue")
        assertEquals("TONE", cues.getString("style"))
        if (!keepRecordings) result.file!!.delete()
    }

    @Test
    fun heyMetaStartVisorStartsASessionAndIsAnswered() {
        val glasses = pairWornGlasses()
        val activity = composeTestRule.activity
        composeTestRule.runOnUiThread { activity.viewModel.home() } // logged in
        val voice = glasses.services.voiceInvocation
        composeTestRule.waitUntil(20_000) { voice.hasConnectedApps() }

        val requestId = voice.simulateLaunchAppAction()
        assertNotNull("VISOR was not listening for voice launches", requestId)
        composeTestRule.waitUntil(10_000) { activity.viewModel.uiState.value.isSessionActive }
        // Answered exactly once: nothing left waiting.
        composeTestRule.waitUntil(10_000) { VoiceLaunches.get().pending.value.isEmpty() }
    }

    private companion object {
        val SHORT_TRIAL = Trial(
            id = "T0_instrumentation",
            tier = "B",
            title = "Instrumentation test",
            worn = true,
            prepSec = 1,
            durationSec = 5,
            purpose = "Automated end-to-end check of the Motion capability path.",
            setup = "",
            instructions = emptyList(),
        )
    }
}
