package ucf.visor.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import ucf.visor.data.ThemeElement
import ucf.visor.data.ThemeElement.BACKGROUND
import ucf.visor.data.ThemeElement.BUTTONS
import ucf.visor.data.ThemeElement.HIGHLIGHT
import ucf.visor.data.ThemeElement.OUTLINE
import ucf.visor.data.ThemeElement.SURFACE
import ucf.visor.data.ThemeElement.TEXT

/**
 * One color pair VISOR draws, and the contrast it needs: [TextContrast]
 * (4.5:1) for text, [ControlContrast] (3:1) for the parts of a control you
 * need to see to use it (WCAG 1.4.11).
 *
 * @param parts the parts of a user's own colors (Settings > My colors) the
 *   pair is made from, so the editor can say which to change. A color VISOR
 *   works out itself counts as the part it is worked out from.
 */
data class ContrastCheck(
    val name: String,
    val foreground: Color,
    val background: Color,
    val minimum: Double,
    val parts: Set<ThemeElement>,
) {
    val ratio: Double get() = contrastRatio(foreground, background)
    val passes: Boolean get() = ratio >= minimum
}

/**
 * Every color pair VISOR draws, against the WCAG contrast targets Section 508
 * adopts. One list, used in two places so they can never disagree:
 *
 *  - ThemeContrastTest runs it on VISOR's four looks, so a color changed in
 *    Color.kt that breaks a pair fails the build's unit tests;
 *  - the color editor runs it on a user's own colors as they pick them, and
 *    says which parts are hard to read, and how hard ("2.1:1").
 *
 * See documentation/research/section-508-accessibility-guidelines.md.
 */
object ContrastAudit {

    fun checks(colors: VisorColors): List<ContrastCheck> {
        val s = colors.scheme
        val surfaces = surfacesOf(s)
        val checks = mutableListOf<ContrastCheck>()

        // Text is readable on every surface it can sit on.
        for ((surfaceName, surface, surfaceParts) in surfaces) {
            checks += ContrastCheck("onSurface on $surfaceName", s.onSurface, surface, TextContrast, surfaceParts + TEXT)
            checks += ContrastCheck("onSurfaceVariant on $surfaceName", s.onSurfaceVariant, surface, TextContrast, surfaceParts + TEXT)
            // Primary is a text color too: Settings' subheadings, dialog
            // buttons, a focused text field's label.
            checks += ContrastCheck("primary on $surfaceName", s.primary, surface, TextContrast, surfaceParts + BUTTONS)
            // An error message, and a text field's error state.
            checks += ContrastCheck("error on $surfaceName", s.error, surface, TextContrast, surfaceParts)
        }
        checks += ContrastCheck("onBackground on background", s.onBackground, s.background, TextContrast, setOf(BACKGROUND, TEXT))

        // Text is readable on every filled color.
        checks += listOf(
            ContrastCheck("onPrimary on primary", s.onPrimary, s.primary, TextContrast, setOf(BUTTONS)),
            ContrastCheck("onPrimaryContainer on primaryContainer", s.onPrimaryContainer, s.primaryContainer, TextContrast, setOf(HIGHLIGHT)),
            ContrastCheck("onSecondary on secondary", s.onSecondary, s.secondary, TextContrast, setOf(BUTTONS)),
            ContrastCheck("onSecondaryContainer on secondaryContainer", s.onSecondaryContainer, s.secondaryContainer, TextContrast, setOf(HIGHLIGHT)),
            ContrastCheck("onTertiary on tertiary", s.onTertiary, s.tertiary, TextContrast, setOf(BUTTONS)),
            ContrastCheck("onTertiaryContainer on tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer, TextContrast, setOf(HIGHLIGHT)),
            ContrastCheck("onError on error", s.onError, s.error, TextContrast, emptySet()),
            ContrastCheck("onErrorContainer on errorContainer", s.onErrorContainer, s.errorContainer, TextContrast, emptySet()),
            ContrastCheck("inverseOnSurface on inverseSurface", s.inverseOnSurface, s.inverseSurface, TextContrast, setOf(TEXT)),
        )

        // Status messages.
        val status = colors.status
        checks += listOf(
            ContrastCheck("onSuccess on success", status.onSuccess, status.success, TextContrast, emptySet()),
            ContrastCheck("onWarning on warning", status.onWarning, status.warning, TextContrast, emptySet()),
            ContrastCheck("onError on error (status)", status.onError, status.error, TextContrast, emptySet()),
        )

        // Text field, chip, card and slider track outlines, and the ring
        // around a control with keyboard, switch-access or D-pad focus (WCAG 2.4.7).
        for ((surfaceName, surface, surfaceParts) in surfaces) {
            checks += ContrastCheck("outline against $surfaceName", s.outline, surface, ControlContrast, surfaceParts + OUTLINE)
            checks += ContrastCheck("focus ring against $surfaceName", colors.focusRing, surface, ControlContrast, surfaceParts + OUTLINE)
        }

        // VisorSlider and StepIndicator: the filled part is solid primary; the
        // part still to fill is hollow, an outline with the background showing
        // through. The thumb is primary with an onPrimary dot, ringed in the
        // background color so it stands apart from the filled track it sits on.
        checks += listOf(
            ContrastCheck("filled track (primary) against the hollow track (background)", s.primary, s.background, ControlContrast, setOf(BUTTONS, BACKGROUND)),
            ContrastCheck("notch on the filled track (onPrimary on primary)", s.onPrimary, s.primary, ControlContrast, setOf(BUTTONS)),
            ContrastCheck("thumb's ring (background) against the thumb (primary)", s.background, s.primary, ControlContrast, setOf(BUTTONS, BACKGROUND)),
        )

        // A selected chip is filled with primaryContainer inside a primary
        // border. Its edge has to stand out from the page, by its border (the
        // pale fill of the Clarity looks) or by its fill (the solid fill of
        // High Contrast, where the border is the fill's own color).
        val border = ContrastCheck("a selected chip's border against the background", s.primary, s.background, ControlContrast, setOf(BUTTONS, BACKGROUND))
        val fill = ContrastCheck("a selected chip's fill against the background", s.primaryContainer, s.background, ControlContrast, setOf(HIGHLIGHT, BACKGROUND))
        checks += if (fill.passes) fill else border

        return checks
    }

    fun failures(colors: VisorColors): List<ContrastCheck> = checks(colors).filterNot { it.passes }

    /** The pairs too faint to see that [part] is one of, faintest first. */
    fun failuresOf(part: ThemeElement, colors: VisorColors): List<ContrastCheck> =
        failures(colors).filter { part in it.parts }.sortedBy { it.ratio / it.minimum }

    private data class NamedSurface(val name: String, val color: Color, val parts: Set<ThemeElement>)

    /**
     * Each surface VISOR can draw on, and which of a user's colors it is
     * made from: the page, the cards, or a shade between the two.
     */
    private fun surfacesOf(s: ColorScheme): List<NamedSurface> = listOf(
        NamedSurface("background", s.background, setOf(BACKGROUND)),
        NamedSurface("surface", s.surface, setOf(SURFACE)),
        NamedSurface("surfaceVariant", s.surfaceVariant, setOf(SURFACE)),
        NamedSurface("surfaceContainerLowest", s.surfaceContainerLowest, setOf(BACKGROUND)),
        NamedSurface("surfaceContainerLow", s.surfaceContainerLow, setOf(BACKGROUND, SURFACE)),
        NamedSurface("surfaceContainer", s.surfaceContainer, setOf(BACKGROUND, SURFACE)),
        NamedSurface("surfaceContainerHigh", s.surfaceContainerHigh, setOf(SURFACE)),
        NamedSurface("surfaceContainerHighest", s.surfaceContainerHighest, setOf(SURFACE)),
        NamedSurface("surfaceBright", s.surfaceBright, setOf(BACKGROUND, SURFACE)),
        NamedSurface("surfaceDim", s.surfaceDim, setOf(BACKGROUND, SURFACE)),
    )
}
