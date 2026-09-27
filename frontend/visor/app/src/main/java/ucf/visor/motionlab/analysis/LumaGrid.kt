package ucf.visor.motionlab.analysis

import java.nio.ByteBuffer
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Where the analysis grid sits inside a source frame: a centred crop of
 * [cropWidth] x [cropHeight] source pixels starting at ([cropX], [cropY]),
 * box-averaged down by [scale] source pixels per grid pixel.
 *
 * Kept with every recording, because a shift measured on the grid only means
 * anything in source pixels — and from there in degrees — through these numbers.
 */
data class GridGeometry(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val gridWidth: Int,
    val gridHeight: Int,
    val scale: Double,
    val cropX: Double,
    val cropY: Double,
) {
    val cropWidth: Double get() = gridWidth * scale
    val cropHeight: Double get() = gridHeight * scale

    companion object {
        /**
         * The largest centred crop of the source with the grid's aspect ratio.
         * One scale for both axes, so a grid pixel is square in source pixels
         * and a diagonal shift is not distorted on its way back out.
         */
        fun fit(sourceWidth: Int, sourceHeight: Int, gridWidth: Int, gridHeight: Int): GridGeometry {
            require(sourceWidth > 0 && sourceHeight > 0) { "Empty source frame" }
            val scale = min(sourceWidth.toDouble() / gridWidth, sourceHeight.toDouble() / gridHeight)
            return GridGeometry(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                gridWidth = gridWidth,
                gridHeight = gridHeight,
                scale = scale,
                cropX = (sourceWidth - gridWidth * scale) / 2.0,
                cropY = (sourceHeight - gridHeight * scale) / 2.0,
            )
        }
    }
}

/**
 * Reduces the luma (Y) plane of an I420 frame to a small grid for global
 * motion estimation.
 *
 * Box averaging rather than point sampling: a head turn smears the image, and
 * averaging each grid cell over its whole source footprint keeps fine texture
 * from aliasing into false motion. Portrait and landscape sources both work —
 * the grid is laid out long side along the frame's long side.
 *
 * Not thread-safe: one instance per processing thread (it reuses its buffers).
 */
class LumaGridSampler(
    private val longSide: Int = DEFAULT_LONG_SIDE,
    private val shortSide: Int = DEFAULT_SHORT_SIDE,
) {
    private var bytes = ByteArray(0)
    private var cachedGeometry: GridGeometry? = null
    private var xStarts = IntArray(0)
    private var xEnds = IntArray(0)
    private var yStarts = IntArray(0)
    private var yEnds = IntArray(0)

    fun geometryFor(width: Int, height: Int): GridGeometry {
        val cached = cachedGeometry
        if (cached != null && cached.sourceWidth == width && cached.sourceHeight == height) return cached
        val portrait = height >= width
        val gridWidth = if (portrait) shortSide else longSide
        val gridHeight = if (portrait) longSide else shortSide
        val geometry = GridGeometry.fit(width, height, gridWidth, gridHeight)
        xStarts = IntArray(gridWidth) { start(geometry.cropX + it * geometry.scale, width) }
        xEnds = IntArray(gridWidth) {
            max(xStarts[it] + 1, end(geometry.cropX + (it + 1) * geometry.scale, width))
        }
        yStarts = IntArray(gridHeight) { start(geometry.cropY + it * geometry.scale, height) }
        yEnds = IntArray(gridHeight) {
            max(yStarts[it] + 1, end(geometry.cropY + (it + 1) * geometry.scale, height))
        }
        cachedGeometry = geometry
        return geometry
    }

    /**
     * Samples the Y plane — the first `width * height` bytes of an I420 buffer —
     * into [out] (row-major, grid-sized). The buffer's position is left as it
     * was: the SDK may still be reading the same frame.
     *
     * @return the grid geometry, or null when the buffer is too short to hold a
     *   luma plane of the stated size.
     */
    fun sample(buffer: ByteBuffer, width: Int, height: Int, out: DoubleArray): GridGeometry? {
        val lumaSize = width * height
        if (width <= 0 || height <= 0 || buffer.remaining() < lumaSize) return null
        if (bytes.size < lumaSize) bytes = ByteArray(lumaSize)
        buffer.duplicate().get(bytes, 0, lumaSize)
        return sample(bytes, width, height, out)
    }

    /** As [sample] for a luma plane already copied into [luma] (row-major, tightly packed). */
    fun sample(luma: ByteArray, width: Int, height: Int, out: DoubleArray): GridGeometry? {
        if (width <= 0 || height <= 0 || luma.size < width * height) return null
        val geometry = geometryFor(width, height)
        require(out.size == geometry.gridWidth * geometry.gridHeight) {
            "Output must hold ${geometry.gridWidth}x${geometry.gridHeight} values"
        }
        for (gy in 0 until geometry.gridHeight) {
            val y0 = yStarts[gy]
            val y1 = yEnds[gy]
            for (gx in 0 until geometry.gridWidth) {
                val x0 = xStarts[gx]
                val x1 = xEnds[gx]
                var sum = 0L
                for (y in y0 until y1) {
                    var index = y * width + x0
                    for (x in x0 until x1) {
                        sum += luma[index].toInt() and 0xFF
                        index++
                    }
                }
                out[gy * geometry.gridWidth + gx] = sum.toDouble() / ((y1 - y0) * (x1 - x0))
            }
        }
        return geometry
    }

    /** First source index a cell covers (inclusive). */
    private fun start(position: Double, limit: Int): Int =
        floor(position).toInt().coerceIn(0, limit - 1)

    /** One past the last source index a cell covers (exclusive). */
    private fun end(position: Double, limit: Int): Int =
        floor(position).toInt().coerceIn(0, limit)

    companion object {
        const val DEFAULT_LONG_SIDE = 256
        const val DEFAULT_SHORT_SIDE = 128
    }
}
