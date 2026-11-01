package ucf.visor.capture

/**
 * Whether a photo is black: of nothing at all, because the camera was covered.
 * With the phone's camera that is the likeliest way a picture goes wrong, and
 * one a low-vision user can't see for themselves: the phone is still in a
 * pocket or bag, face down on the table, or a finger is over the lens. Reading
 * a black photo for text would answer "No text found. Try moving closer.",
 * which sends them looking for the wrong problem.
 *
 * A dim room is not black: some part of it is lit (a window, a lamp, a
 * screen), so a photo is only black when it is dark on average *and* nowhere
 * brighter than [MAX_BRIGHTEST].
 */
object BlackPhoto {

    /** Side of the small copy of the photo [isBlack] is given, in pixels: enough to find a lit window. */
    const val SAMPLE_SIDE = 16

    // Out of 255. A covered phone camera, at its highest gain, averages well under 10.
    private const val MAX_MEAN = 16
    private const val MAX_BRIGHTEST = 48

    /** [pixels]: ARGB, as `Bitmap.getPixels` gives them, from a small copy of the photo. */
    fun isBlack(pixels: IntArray): Boolean {
        if (pixels.isEmpty()) return false
        var sum = 0L
        var brightest = 0
        for (pixel in pixels) {
            val luma = luma(pixel)
            sum += luma
            if (luma > brightest) brightest = luma
        }
        return sum / pixels.size < MAX_MEAN && brightest < MAX_BRIGHTEST
    }

    /** How bright [argb] looks, 0 to 255 (Rec. 601 weights). */
    internal fun luma(argb: Int): Int {
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        return (299 * red + 587 * green + 114 * blue) / 1000
    }
}
