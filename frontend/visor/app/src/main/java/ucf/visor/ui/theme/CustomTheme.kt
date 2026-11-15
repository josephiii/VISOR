package ucf.visor.ui.theme

import androidx.compose.ui.graphics.Color
import ucf.visor.data.CustomColors
import ucf.visor.data.ThemeBase
import ucf.visor.data.ThemeElement

/**
 * A user's own colors (Settings > Text and display > My colors), worked out
 * into every color VISOR draws with.
 *
 * The user picks at most six ([ThemeElement]); everything else follows from
 * them, by three rules:
 *
 *  1. **What isn't touched stays as it was.** Each color the user didn't
 *     change, and nothing they changed feeds into, keeps the exact color of
 *     the look they started from ([CustomColors.base]). Unchanged, the result
 *     is that look exactly.
 *  2. **What follows from a change follows it.** A new page color tints the
 *     cards to match (unless the user picked the cards' color too), and a new
 *     button color carries over to what is selected.
 *  3. **What VISOR works out is always readable.** Text on a filled button,
 *     secondary text, error, warning and success messages are chosen to reach
 *     4.5:1 wherever they appear. So the only pairs ContrastAudit can find
 *     too faint involve a color the user picked, which the editor names and
 *     can fix ("Make readable").
 */
fun customVisorColors(custom: CustomColors): VisorColors {
    val base = AppTheme.of(custom.base)
    val baseColors = VisorColors.of(base)
    if (custom.isUnchanged) return baseColors

    val b = baseColors.scheme
    val chosen = custom.chosen.keys
    val keys = keyColors(custom)
    val bg = keys.getValue(ThemeElement.BACKGROUND)
    val surface = keys.getValue(ThemeElement.SURFACE)
    val text = keys.getValue(ThemeElement.TEXT)
    val buttons = keys.getValue(ThemeElement.BUTTONS)
    val highlight = keys.getValue(ThemeElement.HIGHLIGHT)
    val outline = keys.getValue(ThemeElement.OUTLINE)
    val isDark = isDarkColor(bg)

    val surfacesChanged = ThemeElement.BACKGROUND in chosen || ThemeElement.SURFACE in chosen
    val textChanged = ThemeElement.TEXT in chosen
    val buttonsChanged = ThemeElement.BUTTONS in chosen
    val highlightChanged = highlight != b.primaryContainer
    val outlineChanged = ThemeElement.OUTLINE in chosen

    // Cards from the plainest to the most raised, between the page and the
    // card color, as VISOR's own looks have them.
    val surfaces = if (surfacesChanged) {
        Surfaces(
            lowest = bg,
            low = mix(bg, surface, 0.5f),
            container = mix(bg, surface, 0.75f),
            high = surface,
            highest = mix(surface, text, 0.05f),
            variant = mix(surface, text, 0.05f),
            bright = if (isDark) mix(surface, text, 0.08f) else bg,
            dim = if (isDark) bg else mix(surface, text, 0.08f),
        )
    } else {
        Surfaces(
            lowest = b.surfaceContainerLowest,
            low = b.surfaceContainerLow,
            container = b.surfaceContainer,
            high = b.surfaceContainerHigh,
            highest = b.surfaceContainerHighest,
            variant = b.surfaceVariant,
            bright = b.surfaceBright,
            dim = b.surfaceDim,
        )
    }
    val allSurfaces = listOf(bg, surface) + surfaces.all

    // Secondary text: a little softer than the text, where that stays readable.
    val supportingText = when {
        !textChanged && !surfacesChanged -> b.onSurfaceVariant
        else -> mix(text, surface, 0.18f).takeIf { soft ->
            allSurfaces.all { contrastRatio(soft, it) >= TextContrast }
        } ?: text
    }

    val primary = if (buttonsChanged) buttons else b.primary
    val onPrimary = if (buttonsChanged) readableOn(primary, preferred = bg) else b.onPrimary
    val container = if (highlightChanged) highlight else b.primaryContainer
    val onContainer = if (highlightChanged) readableOn(container, preferred = text) else b.onPrimaryContainer

    val status = if (surfacesChanged) readableStatus(isDark, allSurfaces, bg) else baseColors.status

    val scheme = b.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        // VISOR's screens use neither, but Material's own parts may: they
        // follow the buttons and what is selected, as in VISOR's own looks.
        secondary = if (buttonsChanged) primary else b.secondary,
        onSecondary = if (buttonsChanged) onPrimary else b.onSecondary,
        secondaryContainer = if (highlightChanged) container else b.secondaryContainer,
        onSecondaryContainer = if (highlightChanged) onContainer else b.onSecondaryContainer,
        tertiary = if (buttonsChanged) primary else b.tertiary,
        onTertiary = if (buttonsChanged) onPrimary else b.onTertiary,
        tertiaryContainer = if (highlightChanged) container else b.tertiaryContainer,
        onTertiaryContainer = if (highlightChanged) onContainer else b.onTertiaryContainer,
        background = bg,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaces.variant,
        onSurfaceVariant = supportingText,
        surfaceContainerLowest = surfaces.lowest,
        surfaceContainerLow = surfaces.low,
        surfaceContainer = surfaces.container,
        surfaceContainerHigh = surfaces.high,
        surfaceContainerHighest = surfaces.highest,
        surfaceBright = surfaces.bright,
        surfaceDim = surfaces.dim,
        outline = outline,
        outlineVariant = if (outlineChanged) mix(outline, bg, 0.5f) else b.outlineVariant,
        error = status.error,
        onError = status.onError,
        errorContainer = status.error,
        onErrorContainer = status.onError,
        inverseSurface = text,
        inverseOnSurface = readableOn(text, preferred = bg),
        surfaceTint = primary,
    )
    return VisorColors(
        scheme = scheme,
        status = status,
        focusRing = keys.getValue(ThemeElement.OUTLINE).takeIf { outlineChanged } ?: baseColors.focusRing,
        isDark = isDark,
    )
}

/**
 * The color each of the six parts has in [custom]: the one the user picked,
 * or else the one it follows from (rule 2 of [customVisorColors]), or else
 * the starting look's own. What the editor shows on each part's swatch.
 */
fun keyColors(custom: CustomColors): Map<ThemeElement, Color> {
    val b = colorSchemeFor(AppTheme.of(custom.base))
    val chosen = custom.chosen.mapValues { Color(it.value) }
    val highContrast = custom.base.highContrast

    val bg = chosen[ThemeElement.BACKGROUND] ?: b.background
    val buttons = chosen[ThemeElement.BUTTONS] ?: b.primary
    val text = chosen[ThemeElement.TEXT] ?: b.onSurface
    val surface = chosen[ThemeElement.SURFACE] ?: when {
        ThemeElement.BACKGROUND !in chosen -> b.surface
        // High contrast sets cards apart by their borders, not a tint.
        highContrast -> bg
        else -> mix(bg, buttons, CardTint)
    }
    val following = ThemeElement.BUTTONS in chosen || ThemeElement.BACKGROUND in chosen
    val highlight = chosen[ThemeElement.HIGHLIGHT] ?: when {
        !following -> b.primaryContainer
        // High contrast fills what is selected solid, in the button color.
        highContrast -> buttons
        else -> mix(buttons, bg, HighlightPaleness)
    }
    val outline = chosen[ThemeElement.OUTLINE] ?: b.outline
    return mapOf(
        ThemeElement.BACKGROUND to bg,
        ThemeElement.SURFACE to surface,
        ThemeElement.TEXT to text,
        ThemeElement.BUTTONS to buttons,
        ThemeElement.HIGHLIGHT to highlight,
        ThemeElement.OUTLINE to outline,
    )
}

/**
 * "Make readable": [custom] with [part] made just light or dark enough that
 * nothing it is drawn with is too faint, keeping its hue; null if no
 * lightness does it (the other color of the pair would have to change).
 *
 * Works out the whole look for each lightness it tries, so it can take a
 * moment: call it off the main thread.
 */
fun makeReadable(custom: CustomColors, part: ThemeElement): CustomColors? {
    val current = keyColors(custom).getValue(part)
    val fixed = nearestByLightness(current) { candidate ->
        ContrastAudit.failuresOf(part, customVisorColors(custom.with(part, candidate.toArgbInt()))).isEmpty()
    } ?: return null
    return custom.with(part, fixed.toArgbInt())
}

/** The colors [base] starts with, for the editor's "Start from" swatches. */
fun keyColors(base: ThemeBase): Map<ThemeElement, Color> = keyColors(CustomColors(base))

/**
 * Error, warning and success, kept recognizably red, amber and green but made
 * light or dark enough to read on every surface ([surfaces]) of a page that is
 * [dark] or not. Each moves only as far as it must. Where no lightness reads
 * on them all (cards far darker than the page, say), it keeps the starting
 * color, and ContrastAudit says so.
 */
private fun readableStatus(dark: Boolean, surfaces: List<Color>, bg: Color): VisorStatusColors {
    val start = statusColorsFor(if (dark) AppTheme.ClarityDark else AppTheme.ClarityLight)
    fun readable(color: Color): Color =
        nearestByLightness(color) { candidate ->
            surfaces.all { contrastRatio(candidate, it) >= TextContrast }
        } ?: color
    val success = readable(start.success)
    val warning = readable(start.warning)
    val error = readable(start.error)
    return VisorStatusColors(
        success = success,
        onSuccess = readableOn(success, preferred = bg),
        warning = warning,
        onWarning = readableOn(warning, preferred = bg),
        error = error,
        onError = readableOn(error, preferred = bg),
    )
}

private class Surfaces(
    val lowest: Color,
    val low: Color,
    val container: Color,
    val high: Color,
    val highest: Color,
    val variant: Color,
    val bright: Color,
    val dim: Color,
) {
    val all get() = listOf(lowest, low, container, high, highest, variant, bright, dim)
}

/** How far a card leans from the page toward the button color: the Clarity looks' faint tint. */
private const val CardTint = 0.08f

/** How far a selected chip's fill leans from the button color toward the page: Clarity's pale fill. */
private const val HighlightPaleness = 0.8f
