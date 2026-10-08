package ucf.visor.motionlab.analysis

import kotlin.math.floor
import kotlin.random.Random

/**
 * A deterministic, smoothly textured "scene" larger than any camera view of
 * it, so tests can cut two views of it a known (sub-pixel) distance apart —
 * the ground truth phase correlation is checked against.
 */
class SyntheticScene(val width: Int, val height: Int, seed: Int) {

    private val pixels = DoubleArray(width * height)

    init {
        val random = Random(seed)
        // Three octaves of bilinearly interpolated value noise: coarse shapes,
        // medium detail, fine texture — roughly what a furnished room looks like
        // once downsampled.
        for ((cell, amplitude) in listOf(32 to 90.0, 8 to 45.0, 3 to 20.0)) {
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
                    val a = grid[y0 * gw + x0]
                    val b = grid[y0 * gw + x0 + 1]
                    val c = grid[(y0 + 1) * gw + x0]
                    val d = grid[(y0 + 1) * gw + x0 + 1]
                    val top = a + (b - a) * tx
                    val bottom = c + (d - c) * tx
                    pixels[y * width + x] += amplitude * (top + (bottom - top) * ty)
                }
            }
        }
        for (i in pixels.indices) pixels[i] = (pixels[i] + 128.0).coerceIn(0.0, 255.0)
    }

    /** Bilinear sample at a (possibly fractional) scene position. */
    fun at(x: Double, y: Double): Double {
        val x0 = floor(x).toInt().coerceIn(0, width - 2)
        val y0 = floor(y).toInt().coerceIn(0, height - 2)
        val tx = (x - x0).coerceIn(0.0, 1.0)
        val ty = (y - y0).coerceIn(0.0, 1.0)
        val a = pixels[y0 * width + x0]
        val b = pixels[y0 * width + x0 + 1]
        val c = pixels[(y0 + 1) * width + x0]
        val d = pixels[(y0 + 1) * width + x0 + 1]
        val top = a + (b - a) * tx
        val bottom = c + (d - c) * tx
        return top + (bottom - top) * ty
    }

    /**
     * A [w] x [h] view whose top-left corner sits at ([originX], [originY]).
     * Moving the origin by -d makes the content appear to move by +d.
     */
    fun view(originX: Double, originY: Double, w: Int, h: Int, noise: Double = 0.0, seed: Int = 0): DoubleArray {
        val random = Random(seed)
        return DoubleArray(w * h) { i ->
            val v = at(originX + i % w, originY + i / w)
            if (noise > 0) v + random.nextDouble(-noise, noise) else v
        }
    }
}
