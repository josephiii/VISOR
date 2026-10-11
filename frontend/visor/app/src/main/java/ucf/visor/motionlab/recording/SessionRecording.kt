package ucf.visor.motionlab.recording

import ucf.visor.motionlab.recording.SessionRecording.Companion.MOTION_COLUMNS
import ucf.visor.motionlab.recording.SessionRecording.Companion.MOTION_SOURCE_CODES
import ucf.visor.motionlab.recording.SessionRecording.Companion.VIDEO_COLUMNS
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.zip.GZIPOutputStream

/**
 * One head-motion lab trial as recorded on the phone: glasses IMU samples,
 * camera frame timing with the image shift measured on each frame, and the
 * protocol's marks — serialized as `visor.imu.session/2`.
 *
 * `/2` extends the web IMU Lab's `visor.imu.session/1` (same envelope: `meta`,
 * `marks`, column-oriented `streams` with `nullCounts`) with two native streams:
 *
 * - `dat_motion` — every MWDAT `MotionSample`, in SI units as the SDK delivers
 *   them (m/s², rad/s, µT, unit quaternion). See [MOTION_COLUMNS].
 * - `dat_video` — one row per camera frame. See [VIDEO_COLUMNS].
 *
 * **Three clocks, all kept.** Each row carries the phone's arrival time
 * (`tPhone`, `SystemClock.elapsedRealtimeNanos`) *and* the time the glasses
 * stamped it with (`tDevice` for motion, `tPts` for video). The SDK documents
 * the motion clock as the device's monotonic clock and does not document which
 * clock video presentation timestamps use, so nothing here assumes the two
 * agree: the absolute origins are stored in `meta.clocks` so the analysis can
 * test that, and the IMU-to-camera lag is *measured* by cross-correlation
 * rather than trusted.
 *
 * All times in the file are milliseconds relative to an origin, which keeps
 * them exact in a JSON double; the origins are written as decimal strings
 * because they are 64-bit.
 *
 * Thread-safe: the motion collector, the video collector, the analysis worker
 * and the trial runner all write concurrently.
 */
class SessionRecording(
    private val meta: MutableMap<String, Any?>,
    private val t0Nanos: Long,
    private val t0EpochMs: Long,
    private val startedAtIso: String,
) {
    private val lock = Any()
    private val motion = ColumnTable(MOTION_COLUMNS)
    private val video = ColumnTable(VIDEO_COLUMNS)
    private val marks = mutableListOf<Mark>()
    private var motionOriginNs: Long? = null
    private var videoOriginUs: Long? = null
    private var stopNanos: Long? = null

    data class Mark(val tMs: Double, val label: String, val extra: Map<String, Any?>?)

    /** Live counters for the recording screen. */
    data class Counts(
        val motionSamples: Int,
        val videoFrames: Int,
        val analyzedFrames: Int,
        val elapsedMs: Double,
    )

    fun motionSample(
        phoneNanos: Long,
        deviceNanos: Long,
        accel: FloatArray?,
        gyro: FloatArray?,
        mag: FloatArray?,
        quaternionWxyz: FloatArray?,
        sourceCode: Int,
    ) = synchronized(lock) {
        val origin = motionOriginNs ?: deviceNanos.also { motionOriginNs = it }
        motion.append(
            relativeMs(phoneNanos),
            (deviceNanos - origin) / 1e6,
            component(accel, 0), component(accel, 1), component(accel, 2),
            component(gyro, 0), component(gyro, 1), component(gyro, 2),
            component(mag, 0), component(mag, 1), component(mag, 2),
            component(quaternionWxyz, 0), component(quaternionWxyz, 1),
            component(quaternionWxyz, 2), component(quaternionWxyz, 3),
            sourceCode.toDouble(),
        )
    }

    /** Records a frame's arrival; returns its row index for [frameAnalysis]. */
    fun videoFrame(phoneNanos: Long, ptsUs: Long, width: Int, height: Int): Int =
        synchronized(lock) {
            val origin = videoOriginUs ?: ptsUs.also { videoOriginUs = it }
            video.append(
                relativeMs(phoneNanos),
                (ptsUs - origin) / 1e3,
                width.toDouble(),
                height.toDouble(),
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
            )
        }

    /**
     * Fills in a frame's image-shift columns once the analysis worker has it.
     * [shiftX]/[shiftY] are in **source-frame pixels**; [referenceRow] is the
     * earlier frame the shift was measured against (usually the previous one;
     * earlier when frames were skipped under load).
     */
    fun frameAnalysis(
        row: Int,
        shiftX: Double,
        shiftY: Double,
        peak: Double,
        textureSd: Double,
        referenceRow: Int?,
        analysisMs: Double,
    ) = synchronized(lock) {
        video[row, "shiftX"] = shiftX
        video[row, "shiftY"] = shiftY
        video[row, "peak"] = peak
        video[row, "textureSd"] = textureSd
        video[row, "refIndex"] = referenceRow?.toDouble() ?: Double.NaN
        video[row, "analysisMs"] = analysisMs
    }

    fun mark(phoneNanos: Long, label: String, extra: Map<String, Any?>? = null) =
        synchronized(lock) { marks += Mark(relativeMs(phoneNanos), label, extra) }

    fun putMeta(key: String, value: Any?) = synchronized(lock) { meta[key] = value }

    fun stop(phoneNanos: Long) = synchronized(lock) {
        if (stopNanos == null) stopNanos = phoneNanos
    }

    fun counts(nowNanos: Long): Counts = synchronized(lock) {
        var analyzed = 0
        val peaks = video.column("peak")
        for (p in peaks) if (!p.isNaN()) analyzed++
        Counts(
            motionSamples = motion.size,
            videoFrames = video.size,
            analyzedFrames = analyzed,
            elapsedMs = relativeMs(stopNanos ?: nowNanos),
        )
    }

    /** A consistent copy of one motion column (for the on-phone quick look). */
    fun motionColumn(name: String): DoubleArray = synchronized(lock) { motion.column(name) }

    /** A consistent copy of one video column (for the on-phone quick look). */
    fun videoColumn(name: String): DoubleArray = synchronized(lock) { video.column(name) }

    /** When each mark with [label] was made, ms since recording start, in order. */
    fun markTimes(label: String): DoubleArray = synchronized(lock) {
        marks.filter { it.label == label }.map { it.tMs }.toDoubleArray()
    }

    fun metaValue(key: String): Any? = synchronized(lock) { meta[key] }

    fun writeJson(out: Writer) = synchronized(lock) {
        val json = JsonWriter(out)
        val duration = stopNanos?.let { relativeMs(it) }
        json.beginObject()
        json.name("schema").value(SCHEMA)
        json.name("startedAt").value(startedAtIso)
        json.name("t0Epoch").value(t0EpochMs)
        json.name("durationMs").value(duration, decimals = 3)
        json.name("meta").any(
            meta + mapOf(
                "clocks" to mapOf(
                    "phoneClock" to "SystemClock.elapsedRealtimeNanos",
                    "phoneOriginNs" to t0Nanos.toString(),
                    "motionDeviceOriginNs" to motionOriginNs?.toString(),
                    "videoPtsOriginUs" to videoOriginUs?.toString(),
                ),
                "motionSourceCodes" to MOTION_SOURCE_CODES,
            ),
        )
        json.name("marks").beginArray()
        for (mark in marks) {
            json.beginObject()
            json.name("t").value(mark.tMs, decimals = 3)
            json.name("label").value(mark.label)
            json.name("extra").any(mark.extra)
            json.endObject()
        }
        json.endArray()
        json.name("streams").beginObject()
        json.name("dat_motion")
        writeTable(json, motion, MOTION_DECIMALS)
        json.name("dat_video")
        writeTable(json, video, VIDEO_DECIMALS)
        json.endObject()
        json.endObject()
        json.flush()
    }

    /** Writes the session as gzipped JSON — the format the ingest API and analysis read. */
    fun writeGzip(file: File) {
        file.parentFile?.mkdirs()
        val partial = File(file.parentFile, file.name + ".partial")
        BufferedWriter(
            OutputStreamWriter(GZIPOutputStream(FileOutputStream(partial)), Charsets.UTF_8),
            1 shl 16,
        ).use { writeJson(it) }
        // Only a complete file ever carries the final name.
        if (!partial.renameTo(file)) {
            partial.copyTo(file, overwrite = true)
            partial.delete()
        }
    }

    private fun writeTable(json: JsonWriter, table: ColumnTable, decimals: Map<String, Int>) {
        json.beginObject()
        json.name("n").value(table.size.toLong())
        json.name("nullCounts").any(table.nullCounts())
        json.name("columns").beginObject()
        for (name in table.names) {
            json.name(name).doubles(
                table.column(name),
                decimals = decimals[name] ?: JsonWriter.DEFAULT_DECIMALS,
            )
        }
        json.endObject()
        json.endObject()
    }

    private fun relativeMs(nanos: Long): Double = (nanos - t0Nanos) / 1e6

    private fun component(values: FloatArray?, index: Int): Double =
        values?.getOrNull(index)?.toDouble() ?: Double.NaN

    companion object {
        const val SCHEMA = "visor.imu.session/2"

        /**
         * `tPhone` ms since recording start (phone clock); `tDevice` ms since the
         * first sample (glasses clock); accelerometer `ax..az` m/s² including
         * gravity; gyroscope `gx..gz` rad/s; magnetometer `mx..mz` µT; fused
         * orientation `qw..qz`; `source` per [MOTION_SOURCE_CODES].
         */
        val MOTION_COLUMNS = listOf(
            "tPhone", "tDevice",
            "ax", "ay", "az",
            "gx", "gy", "gz",
            "mx", "my", "mz",
            "qw", "qx", "qy", "qz",
            "source",
        )

        /**
         * `tPhone` ms since recording start (phone clock); `tPts` ms since the
         * first frame's presentation timestamp; frame size; the image shift from
         * `refIndex` to this frame in source pixels (+x right, +y down); the
         * correlation `peak` (0–1) and luma `textureSd` that qualify it; and how
         * long the analysis took on the phone.
         */
        val VIDEO_COLUMNS = listOf(
            "tPhone", "tPts", "width", "height",
            "shiftX", "shiftY", "peak", "textureSd", "refIndex", "analysisMs",
        )

        val MOTION_SOURCE_CODES = mapOf("0" to "GLASSES", "1" to "NEURAL_BAND", "2" to "UNKNOWN")

        private val MOTION_DECIMALS = mapOf("tPhone" to 4, "tDevice" to 4, "source" to 0)
        private val VIDEO_DECIMALS = mapOf(
            "tPhone" to 4, "tPts" to 4, "width" to 0, "height" to 0,
            "shiftX" to 4, "shiftY" to 4, "peak" to 4, "textureSd" to 3,
            "refIndex" to 0, "analysisMs" to 3,
        )
    }
}
