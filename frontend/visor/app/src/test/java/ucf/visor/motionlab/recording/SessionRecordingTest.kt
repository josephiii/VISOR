package ucf.visor.motionlab.recording

import java.io.File
import java.io.StringWriter
import java.util.zip.GZIPInputStream
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionRecordingTest {

    @get:Rule val folder = TemporaryFolder()

    private val t0 = 5_000_000_000L // phone elapsedRealtimeNanos at recording start

    private fun recording() = SessionRecording(
        meta = mutableMapOf("trialId" to "V1_vor_yaw_paced", "participant" to "bench01", "worn" to true),
        t0Nanos = t0,
        t0EpochMs = 1_790_000_000_000L,
        startedAtIso = "2026-09-25T12:00:00.000Z",
    )

    private fun parse(rec: SessionRecording): JSONObject =
        JSONObject(StringWriter().also { rec.writeJson(it) }.toString())

    @Test
    fun writesTheV2EnvelopeWithRelativeTimesAndExactOrigins() {
        val rec = recording()
        val deviceOrigin = 987_654_321_012_345L
        rec.motionSample(
            phoneNanos = t0 + 10_000_000, deviceNanos = deviceOrigin,
            accel = floatArrayOf(0f, 9.81f, 0f), gyro = floatArrayOf(0.1f, -0.2f, 0.3f),
            mag = null, quaternionWxyz = floatArrayOf(1f, 0f, 0f, 0f), sourceCode = 0,
        )
        rec.motionSample(
            phoneNanos = t0 + 27_000_000, deviceNanos = deviceOrigin + 16_666_667,
            accel = floatArrayOf(0.01f, 9.8f, -0.02f), gyro = null,
            mag = null, quaternionWxyz = null, sourceCode = 1,
        )
        rec.mark(t0 + 500_000, "trial_start", mapOf("trialId" to "V1_vor_yaw_paced"))
        rec.stop(t0 + 30_000_000_000)

        val json = parse(rec)
        assertEquals("visor.imu.session/2", json.getString("schema"))
        assertEquals(30_000.0, json.getDouble("durationMs"), 1e-9)
        assertEquals("V1_vor_yaw_paced", json.getJSONObject("meta").getString("trialId"))
        val clocks = json.getJSONObject("meta").getJSONObject("clocks")
        assertEquals(deviceOrigin.toString(), clocks.getString("motionDeviceOriginNs"))
        assertTrue(clocks.isNull("videoPtsOriginUs"))

        val motion = json.getJSONObject("streams").getJSONObject("dat_motion")
        assertEquals(2, motion.getInt("n"))
        val columns = motion.getJSONObject("columns")
        assertEquals(10.0, columns.getJSONArray("tPhone").getDouble(0), 1e-9)
        assertEquals(16.6667, columns.getJSONArray("tDevice").getDouble(1), 1e-9)
        assertEquals(9.81, columns.getJSONArray("ay").getDouble(0), 1e-6)
        assertEquals(1, columns.getJSONArray("source").getInt(1))
        // Absent readings are null, never zero — and counted.
        assertTrue(columns.getJSONArray("gx").isNull(1))
        assertTrue(columns.getJSONArray("mx").isNull(0))
        val nulls = motion.getJSONObject("nullCounts")
        assertEquals(2, nulls.getInt("mx"))
        assertEquals(1, nulls.getInt("gx"))
        assertEquals(1, nulls.getInt("qw"))
        assertEquals(0, nulls.getInt("ax"))

        val mark = json.getJSONArray("marks").getJSONObject(0)
        assertEquals("trial_start", mark.getString("label"))
        assertEquals(0.5, mark.getDouble("t"), 1e-9)
    }

    @Test
    fun videoRowsAreFilledInWhenTheAnalysisArrives() {
        val rec = recording()
        val first = rec.videoFrame(t0 + 40_000_000, ptsUs = 1_000_000, width = 360, height = 640)
        val second = rec.videoFrame(t0 + 73_000_000, ptsUs = 1_033_333, width = 360, height = 640)
        rec.frameAnalysis(second, shiftX = 12.5, shiftY = -3.25, peak = 0.61, textureSd = 28.0,
            referenceRow = first, analysisMs = 6.2)
        assertEquals(2, rec.counts(t0).videoFrames)
        assertEquals(1, rec.counts(t0).analyzedFrames)

        val video = parse(rec).getJSONObject("streams").getJSONObject("dat_video")
        val columns = video.getJSONObject("columns")
        assertEquals(33.333, columns.getJSONArray("tPts").getDouble(1), 1e-9)
        assertTrue(columns.getJSONArray("shiftX").isNull(0))
        assertEquals(12.5, columns.getJSONArray("shiftX").getDouble(1), 1e-9)
        assertEquals(0, columns.getJSONArray("refIndex").getInt(1))
        assertEquals(1, video.getJSONObject("nullCounts").getInt("shiftX"))
    }

    @Test
    fun gzipFileRoundTripsAndLeavesNoPartialBehind() {
        val rec = recording()
        rec.motionSample(t0, 1L, floatArrayOf(1f, 2f, 3f), null, null, null, 0)
        rec.stop(t0 + 1_000_000)
        val file = File(folder.root, "sessions/bench01/test.json.gz")
        rec.writeGzip(file)
        assertTrue(file.exists())
        assertFalse(File(file.parentFile, "test.json.gz.partial").exists())
        val text = GZIPInputStream(file.inputStream()).bufferedReader().readText()
        assertEquals("visor.imu.session/2", JSONObject(text).getString("schema"))
    }

    @Test
    fun jsonWriterEscapesAndNests() {
        val out = StringWriter()
        JsonWriter(out).any(
            mapOf(
                "quote" to "a \"b\"\n\\",
                "list" to listOf(1, 2.5, null, true),
                "nested" to mapOf("empty" to emptyList<Int>(), "nan" to Double.NaN),
            ),
        ).flush()
        val json = JSONObject(out.toString())
        assertEquals("a \"b\"\n\\", json.getString("quote"))
        assertEquals(2.5, json.getJSONArray("list").getDouble(1), 1e-12)
        assertTrue(json.getJSONArray("list").isNull(2))
        assertEquals(0, json.getJSONObject("nested").getJSONArray("empty").length())
        assertTrue(json.getJSONObject("nested").isNull("nan"))
    }

    @Test
    fun numbersAreRoundedButIntegralValuesStayIntegral() {
        assertEquals("3", JsonWriter.formatNumber(3.0, 6))
        assertEquals("0.333333", JsonWriter.formatNumber(1.0 / 3, 6))
        assertEquals("-2.5", JsonWriter.formatNumber(-2.5, 3))
        assertEquals("null", JsonWriter.formatNumber(Double.NaN, 3))
        assertEquals("0", JsonWriter.formatNumber(-0.0000001, 3))
    }
}
