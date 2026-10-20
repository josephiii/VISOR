package ucf.visor.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Every color pair VISOR draws, in all four themes, against the WCAG contrast
 * targets Section 508 adopts: 4.5:1 for text, and 3:1 for the parts of a
 * control you need to see to use it (WCAG 1.4.11), such as an outline or how
 * far a slider is filled. A color changed in Color.kt that breaks a pair fails
 * here, naming the theme, the pair and its ratio.
 */
class ThemeContrastTest {

    @Test
    fun textIsReadableOnEverySurfaceItCanSitOn() = assertAllPass { scheme ->
        surfacesOf(scheme).flatMap { (surfaceName, surface) ->
            listOf(
                Check("onSurface on $surfaceName", scheme.onSurface, surface, TEXT),
                Check("onSurfaceVariant on $surfaceName", scheme.onSurfaceVariant, surface, TEXT),
                // Primary is a text color too: Settings' subheadings, dialog
                // buttons, a focused text field's label.
                Check("primary on $surfaceName", scheme.primary, surface, TEXT),
                // An error message, and a text field's error state.
                Check("error on $surfaceName", scheme.error, surface, TEXT),
            )
        } + Check("onBackground on background", scheme.onBackground, scheme.background, TEXT)
    }

    @Test
    fun textIsReadableOnEveryFilledColor() = assertAllPass { scheme ->
        listOf(
            Check("onPrimary on primary", scheme.onPrimary, scheme.primary, TEXT),
            Check("onPrimaryContainer on primaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer, TEXT),
            Check("onSecondary on secondary", scheme.onSecondary, scheme.secondary, TEXT),
            Check("onSecondaryContainer on secondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer, TEXT),
            Check("onTertiary on tertiary", scheme.onTertiary, scheme.tertiary, TEXT),
            Check("onTertiaryContainer on tertiaryContainer", scheme.onTertiaryContainer, scheme.tertiaryContainer, TEXT),
            Check("onError on error", scheme.onError, scheme.error, TEXT),
            Check("onErrorContainer on errorContainer", scheme.onErrorContainer, scheme.errorContainer, TEXT),
            Check("inverseOnSurface on inverseSurface", scheme.inverseOnSurface, scheme.inverseSurface, TEXT),
        )
    }

    @Test
    fun statusColorsAreReadable() {
        val failures = AppTheme.entries.flatMap { theme ->
            val status = statusColorsFor(theme)
            listOf(
                Check("onSuccess on success", status.onSuccess, status.success, TEXT),
                Check("onWarning on warning", status.onWarning, status.warning, TEXT),
                Check("onError on error", status.onError, status.error, TEXT),
            ).mapNotNull { it.failure(theme) }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun controlEdgesStandOutFromWhatTheySitOn() = assertAllPass { scheme ->
        // Text field, chip, card and slider track outlines.
        surfacesOf(scheme).map { (surfaceName, surface) ->
            Check("outline against $surfaceName", scheme.outline, surface, UI)
        }
    }

    /**
     * A selected chip is filled with primaryContainer inside a primary border.
     * Its edge has to stand out from the page, by its border (the pale fill of
     * the Clarity themes) or by its fill (the solid fill of High Contrast,
     * where the border is the fill's own color).
     */
    @Test
    fun aSelectedChipsEdgeStandsOut() {
        val failures = AppTheme.entries.mapNotNull { theme ->
            val scheme = colorSchemeFor(theme)
            val border = contrastRatio(scheme.primary, scheme.background)
            val fill = contrastRatio(scheme.primaryContainer, scheme.background)
            if (maxOf(border, fill) < UI) {
                "$theme: a selected chip's border is ${"%.2f".format(border)}:1 and its fill " +
                    "${"%.2f".format(fill)}:1 against the background; one needs ${"%.1f".format(UI)}:1"
            } else {
                null
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** The ring around a control with keyboard, switch-access or D-pad focus (WCAG 2.4.7). */
    @Test
    fun aFocusRingStandsOut() {
        val failures = AppTheme.entries.flatMap { theme ->
            surfacesOf(colorSchemeFor(theme)).mapNotNull { (surfaceName, surface) ->
                Check("focus ring against $surfaceName", focusRingFor(theme), surface, UI).failure(theme)
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * VisorSlider and StepIndicator: the filled part is solid primary; the part
     * still to fill is hollow, an outline with the background showing through.
     * The thumb is primary with an onPrimary dot, ringed in the background color
     * so it stands apart from the filled track it sits on.
     */
    @Test
    fun aSliderShowsHowFullItIs() = assertAllPass { scheme ->
        listOf(
            Check("filled track (primary) against the hollow track (background)", scheme.primary, scheme.background, UI),
            Check("hollow track's outline against the background", scheme.outline, scheme.background, UI),
            Check("notch on the filled track (onPrimary on primary)", scheme.onPrimary, scheme.primary, UI),
            Check("notch on the hollow track (outline on background)", scheme.outline, scheme.background, UI),
            Check("thumb's ring (background) against the thumb (primary)", scheme.background, scheme.primary, UI),
        )
    }

    private fun surfacesOf(scheme: ColorScheme): List<Pair<String, Color>> = listOf(
        "background" to scheme.background,
        "surface" to scheme.surface,
        "surfaceVariant" to scheme.surfaceVariant,
        "surfaceContainerLowest" to scheme.surfaceContainerLowest,
        "surfaceContainerLow" to scheme.surfaceContainerLow,
        "surfaceContainer" to scheme.surfaceContainer,
        "surfaceContainerHigh" to scheme.surfaceContainerHigh,
        "surfaceContainerHighest" to scheme.surfaceContainerHighest,
        "surfaceBright" to scheme.surfaceBright,
        "surfaceDim" to scheme.surfaceDim,
    )

    private fun assertAllPass(checks: (ColorScheme) -> List<Check>) {
        val failures = AppTheme.entries.flatMap { theme ->
            checks(colorSchemeFor(theme)).mapNotNull { it.failure(theme) }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private class Check(val name: String, val foreground: Color, val background: Color, val minimum: Double) {
        fun failure(theme: AppTheme): String? {
            val ratio = contrastRatio(foreground, background)
            return if (ratio < minimum) {
                "$theme: $name is ${"%.2f".format(ratio)}:1, needs ${"%.1f".format(minimum)}:1"
            } else {
                null
            }
        }
    }

    private companion object {
        /** WCAG 1.4.3: text and images of text. */
        const val TEXT = 4.5

        /** WCAG 1.4.11: the parts of a control needed to see and use it. */
        const val UI = 3.0

        /** The WCAG contrast ratio of two opaque sRGB colors, from 1:1 to 21:1. */
        fun contrastRatio(a: Color, b: Color): Double {
            val la = relativeLuminance(a)
            val lb = relativeLuminance(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }

        /** WCAG's relative luminance: each sRGB channel linearized, then weighted. */
        fun relativeLuminance(color: Color): Double {
            fun linear(channel: Float): Double {
                val c = channel.toDouble()
                return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
        }
    }
}
