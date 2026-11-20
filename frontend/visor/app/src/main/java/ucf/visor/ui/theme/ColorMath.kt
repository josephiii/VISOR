package ucf.visor.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The color arithmetic behind VISOR's themes and the color picker, in plain
 * Kotlin so it is unit-tested on the JVM (ColorMathTest).
 *
 * Colors are picked in OKLCH (Björn Ottosson's OKLab, as lightness, chroma
 * and hue): its lightness follows how bright a color looks, which is what
 * decides whether text on it can be read. HSV's "value" does not: pure yellow
 * and pure blue share one, yet black text is readable only on the yellow.
 */

/** A color as lightness (0 black to 1 white), chroma (0 gray, up to about 0.37) and hue (degrees). */
data class Oklch(val l: Double, val c: Double, val h: Double)

/** The WCAG contrast ratio of two opaque sRGB colors, from 1:1 to 21:1. */
fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** WCAG's relative luminance: each sRGB channel linearized, then weighted. */
fun relativeLuminance(color: Color): Double =
    0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

/**
 * Whether text in white reads better on [color] than text in black: where the
 * two give the same contrast, about mid-gray, a theme turns from light to dark.
 */
fun isDarkColor(color: Color): Boolean = relativeLuminance(color) < DarkLuminance

/**
 * [preferred] if it is readable on [fill] (4.5:1), otherwise black or white,
 * whichever reads better. One of them always reaches 4.5:1 on any color.
 */
fun readableOn(fill: Color, preferred: Color): Color = when {
    contrastRatio(preferred, fill) >= TextContrast -> preferred
    contrastRatio(Color.Black, fill) >= contrastRatio(Color.White, fill) -> Color.Black
    else -> Color.White
}

/** [from] moved [amount] (0 to 1) of the way toward [to], channel by channel in sRGB. */
fun mix(from: Color, to: Color, amount: Float): Color = Color(
    red = from.red + (to.red - from.red) * amount,
    green = from.green + (to.green - from.green) * amount,
    blue = from.blue + (to.blue - from.blue) * amount,
)

fun Color.toOklch(): Oklch {
    val r = linear(red)
    val g = linear(green)
    val b = linear(blue)
    val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    val lightness = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
    val a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
    val bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
    val chroma = hypot(a, bb)
    val hue = if (chroma < 1e-4) 0.0 else (Math.toDegrees(atan2(bb, a)) + 360.0) % 360.0
    return Oklch(lightness.coerceIn(0.0, 1.0), chroma, hue)
}

/**
 * The sRGB color at [l], [c] and [h]. A chroma the screen can't show at that
 * lightness and hue is brought in to the most it can, so the hue is kept.
 */
fun oklchColor(l: Double, c: Double, h: Double): Color {
    val lightness = l.coerceIn(0.0, 1.0)
    val asked = c.coerceAtLeast(0.0)
    val chroma = if (inGamut(oklchToLinear(lightness, asked, h))) asked else minOf(asked, maxChroma(lightness, h))
    val rgb = oklchToLinear(lightness, chroma, h)
    return Color(
        red = gamma(rgb[0]).toFloat().coerceIn(0f, 1f),
        green = gamma(rgb[1]).toFloat().coerceIn(0f, 1f),
        blue = gamma(rgb[2]).toFloat().coerceIn(0f, 1f),
    )
}

/**
 * The color at [l] and [h] with [colorfulness] (0 to 1) of the most chroma
 * the screen can show there. The picker's Colorfulness slider is this
 * fraction, so every position on it changes the color, at any hue.
 */
fun oklchColorRelative(l: Double, colorfulness: Double, h: Double): Color =
    oklchColor(l, colorfulness.coerceIn(0.0, 1.0) * maxChroma(l.coerceIn(0.0, 1.0), h), h)

/** How far 0 to 1 [color]'s chroma is toward the most the screen can show at its lightness and hue. */
fun colorfulnessOf(color: Color): Double {
    val lch = color.toOklch()
    val max = maxChroma(lch.l, lch.h)
    return if (max < 1e-4) 0.0 else (lch.c / max).coerceIn(0.0, 1.0)
}

/**
 * The most chroma sRGB can show at lightness [l] and hue [h].
 *
 * Found from the outside in, then refined by halving. Halving alone, from
 * gray outward, assumes every chroma up to the edge is showable, and near the
 * gamut's corners it isn't: dark blue (#000044) at 90% of its chroma has a
 * red channel just below zero, so halving would stop well short of the edge.
 */
fun maxChroma(l: Double, h: Double): Double {
    if (l <= 0.0 || l >= 1.0) return 0.0
    var outer = ChromaCeiling
    while (outer > 0.0 && !inGamut(oklchToLinear(l, outer, h))) outer -= ChromaScanStep
    var low = outer.coerceAtLeast(0.0)
    var high = low + ChromaScanStep
    repeat(14) {
        val mid = (low + high) / 2
        if (inGamut(oklchToLinear(l, mid, h))) low = mid else high = mid
    }
    return low
}

/**
 * The color nearest [color] in lightness, with its hue and as much of its
 * chroma as fits, that [accept] takes; null if none does. What "Make
 * readable" uses: it moves a color only as far lighter or darker as it must.
 */
fun nearestByLightness(color: Color, accept: (Color) -> Boolean): Color? {
    if (accept(color)) return color
    val lch = color.toOklch()
    val steps = (1.0 / LightnessSearchStep).roundToInt()
    for (i in 1..steps) {
        val delta = i * LightnessSearchStep
        for (l in doubleArrayOf(lch.l - delta, lch.l + delta)) {
            if (l < 0.0 || l > 1.0) continue
            val candidate = oklchColor(l, lch.c, lch.h)
            if (accept(candidate)) return candidate
        }
    }
    return null
}

/** "#1A4D98". */
fun Color.toHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

/** As CustomColors saves a color: an opaque ARGB int. */
fun Color.toArgbInt(): Int = toArgb() or 0xFF000000.toInt()

/**
 * The color [text] names in hex: "#1A4D98", "1a4d98", or the short "#FC0".
 * Null for anything else, and for a color that isn't opaque.
 */
fun parseHexColor(text: String): Color? {
    val digits = text.trim().removePrefix("#")
    val full = when (digits.length) {
        3 -> digits.map { "$it$it" }.joinToString("")
        6 -> digits
        else -> return null
    }
    val rgb = full.toIntOrNull(16) ?: return null
    return Color(0xFF000000.toInt() or rgb)
}

/**
 * A plain name for [color], such as "dark navy blue" or "light gray", said by
 * a screen reader and shown under each swatch: someone who can't make out a
 * swatch still knows what they picked. In English, as SpokenPhrases is.
 */
fun colorName(color: Color): String {
    val lch = color.toOklch()
    val l = lch.l
    if (lch.c < 0.025) {
        return when {
            l < 0.2 -> "black"
            l < 0.42 -> "very dark gray"
            l < 0.6 -> "dark gray"
            l < 0.78 -> "gray"
            l < 0.95 -> "light gray"
            else -> "white"
        }
    }
    val hue = hueName(lch.h, l)
    val shade = when {
        l < 0.3 -> "very dark "
        l < 0.48 -> "dark "
        l > 0.9 -> "very light "
        l > 0.75 -> "light "
        else -> ""
    }
    val muted = if (colorfulnessOf(color) < 0.3) "grayish " else ""
    // "Brown" and "navy blue" already say how dark; "pink", how light.
    val name = if (hue == "brown" || hue == "navy blue" || hue == "pink") "$muted$hue" else "$shade$muted$hue"
    return name.trim()
}

/**
 * The hue's name, nearest of a dozen. Dark oranges and golds are brown, dark
 * blue is navy, and pale red is pink.
 */
private fun hueName(h: Double, l: Double): String {
    val nearest = HueNames.minBy { (center, _) -> hueDistance(h, center) }.second
    return when {
        (nearest == "orange" || nearest == "gold" || nearest == "yellow") && l < 0.5 -> "brown"
        nearest == "blue" && l < 0.4 -> "navy blue"
        nearest == "red" && l > 0.8 -> "pink"
        else -> nearest
    }
}

private fun hueDistance(a: Double, b: Double): Double {
    val d = abs(a - b) % 360.0
    return if (d > 180.0) 360.0 - d else d
}

/** OKLCH hue angles of each named hue's middle. */
private val HueNames = listOf(
    0.0 to "pink",
    22.0 to "red",
    48.0 to "orange",
    75.0 to "gold",
    100.0 to "yellow",
    125.0 to "lime green",
    145.0 to "green",
    178.0 to "teal",
    210.0 to "sky blue",
    255.0 to "blue",
    295.0 to "purple",
    330.0 to "magenta",
)

private fun oklchToLinear(l: Double, c: Double, h: Double): DoubleArray {
    val rad = Math.toRadians(h)
    val a = c * cos(rad)
    val b = c * sin(rad)
    val l1 = (l + 0.3963377774 * a + 0.2158037573 * b).pow(3)
    val m1 = (l - 0.1055613458 * a - 0.0638541728 * b).pow(3)
    val s1 = (l - 0.0894841775 * a - 1.2914855480 * b).pow(3)
    return doubleArrayOf(
        4.0767416621 * l1 - 3.3077115913 * m1 + 0.2309699292 * s1,
        -1.2684380046 * l1 + 2.6097574011 * m1 - 0.3413193965 * s1,
        -0.0041960863 * l1 - 0.7034186147 * m1 + 1.7076147010 * s1,
    )
}

private fun inGamut(rgb: DoubleArray): Boolean = rgb.all { it >= -GamutSlack && it <= 1.0 + GamutSlack }

private fun linear(channel: Float): Double {
    val c = channel.toDouble()
    return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

private fun gamma(linear: Double): Double {
    val c = linear.coerceIn(0.0, 1.0)
    return if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1 / 2.4) - 0.055
}

/** WCAG 1.4.3: text and images of text. */
const val TextContrast = 4.5

/** WCAG 1.4.11: the parts of a control needed to see and use it. */
const val ControlContrast = 3.0

/** The luminance at which black and white text have the same contrast. */
private const val DarkLuminance = 0.179

private const val LightnessSearchStep = 0.005

/** More chroma than sRGB has anywhere (its most is about 0.32, at blue). */
private const val ChromaCeiling = 0.4
private const val ChromaScanStep = 0.01
/**
 * How far past 0 or 1 a linear channel may land and still count as showable.
 * OKLab's published matrices are inverses only to about this precision, so a
 * color on the gamut's edge (#000011) would otherwise read as just outside it
 * and lose chroma it didn't need to. Far below one 8-bit step; the channels
 * are clamped after.
 */
private const val GamutSlack = 1e-5
