package ucf.visor

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Encodes a test video whose image moves by a known amount every frame: a
 * textured scene panning horizontally at [pixelsPerFrame]. Fed to
 * MockDeviceKit's camera, it gives the head-motion lab's image-shift pipeline
 * a ground truth that runs through the SDK's own decode and frame delivery.
 *
 * HEVC first (what MockDeviceKit's docs ask for), AVC if the device has no
 * HEVC encoder. Uses the platform's own encoders — no test assets to check in.
 */
object PanningVideo {

    fun encode(
        out: File,
        width: Int = 360,
        height: Int = 640,
        fps: Int = 30,
        seconds: Int = 8,
        pixelsPerFrame: Int = 6,
    ): String {
        val mime = listOf(MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AVC)
            .first { hasEncoder(it) }
        val frames = fps * seconds
        val scene = Scene(width + pixelsPerFrame * frames + 64, height)

        val format = MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(mime)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxing = false
        val info = MediaCodec.BufferInfo()
        var frame = 0
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    if (frame == frames) {
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val image = codec.getInputImage(index)!!
                        // Content moves toward -x as the view origin moves right.
                        val originX = frame * pixelsPerFrame
                        val y = image.planes[0]
                        for (row in 0 until height) {
                            for (col in 0 until width) {
                                y.buffer.put(
                                    row * y.rowStride + col * y.pixelStride,
                                    scene.at(originX + col, row)
                                )
                            }
                        }
                        for (p in 1..2) {
                            val plane = image.planes[p]
                            for (row in 0 until height / 2) {
                                for (col in 0 until width / 2) {
                                    plane.buffer.put(
                                        row * plane.rowStride + col * plane.pixelStride,
                                        128.toByte()
                                    )
                                }
                            }
                        }
                        codec.queueInputBuffer(
                            index,
                            0,
                            width * height * 3 / 2,
                            frame * 1_000_000L / fps,
                            0
                        )
                        frame++
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxing = true
                }

                outIndex >= 0 -> {
                    val data = codec.getOutputBuffer(outIndex)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && muxing) {
                        data.position(info.offset)
                        data.limit(info.offset + info.size)
                        muxer.writeSampleData(track, data, info)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
        codec.stop()
        codec.release()
        if (muxing) muxer.stop()
        muxer.release()
        return mime
    }

    private fun hasEncoder(mime: String): Boolean =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }

    /** Deterministic value-noise texture with detail at several scales. */
    private class Scene(val width: Int, val height: Int) {
        private val pixels = ByteArray(width * height)

        init {
            val random = Random(17)
            val values = DoubleArray(width * height)
            for ((cell, amplitude) in listOf(40 to 70.0, 12 to 40.0, 4 to 18.0)) {
                val gw = width / cell + 2
                val gh = height / cell + 2
                val grid = DoubleArray(gw * gh) { random.nextDouble(-1.0, 1.0) }
                for (y in 0 until height) {
                    val fy = y.toDouble() / cell
                    val y0 = floor(fy).toInt()
                    val ty = fy - y0
                    for (x in 0 until width) {
                        val fx = x.toDouble() / cell
                        val x0 = floor(fx).toInt()
                        val tx = fx - x0
                        val top =
                            grid[y0 * gw + x0] + (grid[y0 * gw + x0 + 1] - grid[y0 * gw + x0]) * tx
                        val bottom = grid[(y0 + 1) * gw + x0] +
                                (grid[(y0 + 1) * gw + x0 + 1] - grid[(y0 + 1) * gw + x0]) * tx
                        values[y * width + x] += amplitude * (top + (bottom - top) * ty)
                    }
                }
            }
            // A faint periodic stripe so the scene is never locally uniform.
            for (i in values.indices) {
                val x = i % width
                val v = values[i] + 128 + 10 * sin(2 * PI * x / 23.0)
                pixels[i] = v.coerceIn(16.0, 235.0).toInt().toByte()
            }
        }

        fun at(x: Int, y: Int): Byte = pixels[y * width + x.coerceIn(0, width - 1)]
    }
}
