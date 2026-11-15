package ucf.visor.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The color arithmetic behind the color picker and a user's own colors (ColorMath). */
class ColorMathTest {

    /** Every 17th step of each channel: 4,096 colors across the whole sRGB cube. */
    private val sweep: List<Color> = (0..255 step 17).flatMap { r ->
        (0..255 step 17).flatMap { g ->
            (0..255 step 17).map { b -> Color(red = r / 255f, green = g / 255f, blue = b / 255f) }
        }
    }

    @Test
    fun toOklchMatchesPublishedValues() {
        // Björn Ottosson's OKLab, as CSS Color 4 gives it for sRGB red.
        val red = Color(0xFFFF0000).toOklch()
        assertEquals(0.628, red.l, 0.001)
        assertEquals(0.258, red.c, 0.001)
        assertEquals(29.23, red.h, 0.05)
        assertEquals(0.0, Color.Black.toOklch().l, 1e-6)
        assertEquals(1.0, Color.White.toOklch().l, 1e-4)
        assertEquals(0.0, Color.White.toOklch().c, 1e-4)
    }

    @Test
    fun aColorSurvivesTheRoundTripThroughOklch() {
        sweep.forEach { color ->
            val lch = color.toOklch()
            val back = oklchColor(lch.l, lch.c, lch.h)
            assertTrue(
                "${color.toHex()} came back as ${back.toHex()}",
                abs(back.red - color.red) < 1.5f / 255 &&
                    abs(back.green - color.green) < 1.5f / 255 &&
                    abs(back.blue - color.blue) < 1.5f / 255,
            )
        }
    }

    /** Asked for more chroma than the screen has, a color keeps its hue and lightness. */
    @Test
    fun outOfGamutChromaIsBroughtInKeepingTheHue() {
        for (hue in 0 until 360 step 15) {
            for (l in listOf(0.2, 0.5, 0.8)) {
                val color = oklchColor(l, 0.5, hue.toDouble())
                val lch = color.toOklch()
                assertEquals("lightness at $hue°, $l", l, lch.l, 0.01)
                if (lch.c > 0.03) assertTrue("hue at $hue°, $l was ${lch.h}", hueGap(lch.h, hue.toDouble()) < 3.0)
            }
        }
    }

    @Test
    fun colorfulnessRunsFromGrayToTheMostTheScreenShows() {
        assertEquals(0.0, colorfulnessOf(Color(0xFF999999)), 0.01)
        assertEquals(1.0, colorfulnessOf(Color(0xFFFF0000)), 0.02)
        val half = oklchColorRelative(0.6, 0.5, 250.0)
        assertEquals(0.5, colorfulnessOf(half), 0.03)
    }

    /** Text on a button VISOR works out must always be readable: black or white always is. */
    @Test
    fun readableOnAlwaysReachesTextContrast() {
        sweep.forEach { fill ->
            val ink = readableOn(fill, preferred = fill)
            assertTrue("on ${fill.toHex()}", contrastRatio(ink, fill) >= TextContrast)
        }
    }

    @Test
    fun readableOnKeepsThePreferredColorWhenItReads() {
        val navy = Color(0xFF0E2A47)
        assertEquals(Color.White, readableOn(navy, preferred = Color.White))
        assertEquals(navy, readableOn(Color.White, preferred = navy))
    }

    @Test
    fun nearestByLightnessMovesOnlyAsFarAsItMust() {
        val start = Color(0xFF8899AA)
        val target = 4.5
        val found = nearestByLightness(start) { contrastRatio(it, Color.White) >= target }!!
        assertTrue(contrastRatio(found, Color.White) >= target)
        // Just past the line, not all the way to black.
        assertTrue(contrastRatio(found, Color.White) < target + 0.3)
        // Same hue.
        assertTrue(hueGap(found.toOklch().h, start.toOklch().h) < 5.0)
    }

    @Test
    fun nearestByLightnessIsNullWhenNothingWorks() {
        assertNull(nearestByLightness(Color.Gray) { false })
    }

    @Test
    fun hexCodesReadAndWrite() {
        assertEquals("#1A4D98", Color(0xFF1A4D98).toHex())
        assertEquals(Color(0xFF1A4D98), parseHexColor("#1a4d98"))
        assertEquals(Color(0xFF1A4D98), parseHexColor(" 1A4D98 "))
        assertEquals(Color(0xFFFFCC00), parseHexColor("#FC0"))
        assertNull(parseHexColor("#12345"))
        assertNull(parseHexColor("#GG0000"))
        assertNull(parseHexColor(""))
    }

    @Test
    fun colorsHavePlainNames() {
        mapOf(
            0xFF000000 to "black",
            0xFFFFFFFF to "white",
            0xFF999999 to "gray",
            0xFFFF0000 to "red",
            0xFF1A4D98 to "dark blue",
            0xFF0E2A47 to "navy blue",
            0xFF1B5E20 to "dark green",
        ).forEach { (argb, name) -> assertEquals(name, colorName(Color(argb))) }
        mapOf(
            0xFFFFD400 to "yellow",
            0xFFE65100 to "orange",
            0xFF6B4700 to "brown",
            0xFFFFCDD2 to "pink",
            0xFF4A148C to "purple",
        ).forEach { (argb, word) ->
            val name = colorName(Color(argb))
            assertTrue("${Color(argb).toHex()} is \"$name\"", word in name)
        }
    }

    private fun hueGap(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }
}
