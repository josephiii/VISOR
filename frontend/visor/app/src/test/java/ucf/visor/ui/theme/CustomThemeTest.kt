package ucf.visor.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ucf.visor.data.CustomColors
import ucf.visor.data.ThemeBase
import ucf.visor.data.ThemeElement
import ucf.visor.data.ThemeMode
import ucf.visor.data.UserProfile

/**
 * A user's own colors (Settings > Text and display > My colors), worked out
 * into every color VISOR draws with: customVisorColors and makeReadable.
 */
class CustomThemeTest {

    private fun CustomColors.with(part: ThemeElement, argb: Long) = with(part, argb.toInt())

    @Test
    fun unchangedColorsAreTheStartingLookExactly() {
        ThemeBase.entries.forEach { base ->
            val theme = AppTheme.of(base)
            assertEquals(base, theme.base)
            assertSameColors("$base", VisorColors.of(theme), customVisorColors(CustomColors(base)))
        }
    }

    /** Rule 1: what a change doesn't reach keeps the starting look's exact color. */
    @Test
    fun aChangeLeavesWhatItDoesNotReachAlone() {
        val base = VisorColors.of(AppTheme.ClarityLight).scheme
        val custom = customVisorColors(CustomColors(ThemeBase.CLARITY_LIGHT).with(ThemeElement.OUTLINE, 0xFF000000))
        assertEquals(Color.Black, custom.scheme.outline)
        assertEquals(Color.Black, custom.focusRing)
        assertEquals(base.background, custom.scheme.background)
        assertEquals(base.surface, custom.scheme.surface)
        assertEquals(base.onSurface, custom.scheme.onSurface)
        assertEquals(base.onSurfaceVariant, custom.scheme.onSurfaceVariant)
        assertEquals(base.primary, custom.scheme.primary)
        assertEquals(base.primaryContainer, custom.scheme.primaryContainer)
        assertEquals(base.surfaceContainerLow, custom.scheme.surfaceContainerLow)
    }

    /** Rule 2: a new page tints the cards to match, and new buttons carry over to what is picked. */
    @Test
    fun whatFollowsFromAChangeFollowsIt() {
        val clarity = VisorColors.of(AppTheme.ClarityLight).scheme
        val green = CustomColors(ThemeBase.CLARITY_LIGHT)
            .with(ThemeElement.BACKGROUND, 0xFFF1F8E9)
            .with(ThemeElement.BUTTONS, 0xFF1B5E20)
        val keys = keyColors(green)
        assertNotEquals(clarity.surface, keys[ThemeElement.SURFACE])
        assertNotEquals(clarity.primaryContainer, keys[ThemeElement.HIGHLIGHT])
        // Picked by the user, the cards' color is kept as picked.
        val picked = keyColors(green.with(ThemeElement.SURFACE, 0xFFFFFFFF))
        assertEquals(Color.White, picked[ThemeElement.SURFACE])

        // High contrast keeps its cards the page's color, and fills what is picked solid.
        val hc = keyColors(
            CustomColors(ThemeBase.HIGH_CONTRAST_DARK)
                .with(ThemeElement.BACKGROUND, 0xFF101010)
                .with(ThemeElement.BUTTONS, 0xFFFFD400),
        )
        assertEquals(Color(0xFF101010), hc[ThemeElement.SURFACE])
        assertEquals(Color(0xFFFFD400), hc[ThemeElement.HIGHLIGHT])
    }

    /**
     * Rule 3: whatever the user picks, what VISOR works out itself (text on a
     * button or a picked choice, error, warning and success) stays readable,
     * so every pair the audit finds too faint involves a color they picked.
     */
    @Test
    fun whatVisorWorksOutIsAlwaysReadable() {
        val palette = listOf(
            0xFF000000, 0xFFFFFFFF, 0xFF1A1A1A, 0xFFF5F5DC, 0xFF0E2A47,
            0xFF9CC7FF, 0xFFFFD400, 0xFF8D1B1B, 0xFF1B5E20, 0xFFFF80AB,
        )
        val derivedOnly = setOf(
            "onPrimary on primary",
            "onPrimaryContainer on primaryContainer",
            "onSecondary on secondary",
            "onSecondaryContainer on secondaryContainer",
            "onTertiary on tertiary",
            "onTertiaryContainer on tertiaryContainer",
            "onError on error",
            "onErrorContainer on errorContainer",
            "inverseOnSurface on inverseSurface",
            "onSuccess on success",
            "onWarning on warning",
            "onError on error (status)",
        )
        ThemeBase.entries.forEach { base ->
            palette.forEach { bg ->
                palette.forEach { buttons ->
                    val custom = CustomColors(base)
                        .with(ThemeElement.BACKGROUND, bg)
                        .with(ThemeElement.BUTTONS, buttons)
                    val failures = ContrastAudit.failures(customVisorColors(custom)).filter { it.name in derivedOnly }
                    assertTrue("$base, page ${Color(bg).toHex()}, buttons ${Color(buttons).toHex()}: $failures", failures.isEmpty())
                }
            }
        }
    }

    /** Error messages stay readable on a page of any lightness, and stay red. */
    @Test
    fun errorMessagesFollowThePage() {
        listOf(0xFF000000, 0xFF404040, 0xFF808080, 0xFFC0C0C0, 0xFFFFFFFF).forEach { gray ->
            val colors = customVisorColors(
                CustomColors(ThemeBase.CLARITY_LIGHT)
                    .with(ThemeElement.BACKGROUND, gray)
                    .with(ThemeElement.SURFACE, gray),
            )
            assertTrue(contrastRatio(colors.scheme.error, Color(gray)) >= TextContrast)
            val hue = colors.scheme.error.toOklch().h
            assertTrue("error hue $hue on ${Color(gray).toHex()}", hue < 70.0 || hue > 340.0)
        }
    }

    @Test
    fun darkPagesAreDarkThemes() {
        val dark = customVisorColors(CustomColors(ThemeBase.CLARITY_LIGHT).with(ThemeElement.BACKGROUND, 0xFF101820))
        assertTrue(dark.isDark)
        val light = customVisorColors(CustomColors(ThemeBase.CLARITY_DARK).with(ThemeElement.BACKGROUND, 0xFFFFF8E1))
        assertFalse(light.isDark)
    }

    @Test
    fun makeReadableFixesAFaintPartAndKeepsItsHue() {
        // Light blue text on a white page: about 1.6:1.
        val faint = CustomColors(ThemeBase.CLARITY_LIGHT).with(ThemeElement.TEXT, 0xFF9CC7FF)
        assertFalse(ContrastAudit.failuresOf(ThemeElement.TEXT, customVisorColors(faint)).isEmpty())

        val fixed = makeReadable(faint, ThemeElement.TEXT)
        assertNotNull(fixed)
        assertTrue(ContrastAudit.failuresOf(ThemeElement.TEXT, customVisorColors(fixed!!)).isEmpty())
        val before = Color(0xFF9CC7FF).toOklch()
        val after = keyColors(fixed).getValue(ThemeElement.TEXT).toOklch()
        assertEquals(before.h, after.h, 6.0)
        assertTrue("darker, to read on white", after.l < before.l)
    }

    /** Across many faint pairs, "Make readable" always finds a color that reads. */
    @Test
    fun makeReadableWorksAcrossManyPairs() {
        val pages = listOf(0xFFFFFFFF, 0xFF000000, 0xFFFFF8E1, 0xFF0B1220)
        val texts = listOf(0xFF808080, 0xFF9CC7FF, 0xFF5FE0D3, 0xFFFF8A5C, 0xFF4A148C, 0xFFFFD400)
        pages.forEach { page ->
            texts.forEach { text ->
                val custom = CustomColors(ThemeBase.CLARITY_LIGHT)
                    .with(ThemeElement.BACKGROUND, page)
                    .with(ThemeElement.SURFACE, page)
                    .with(ThemeElement.TEXT, text)
                val fixed = makeReadable(custom, ThemeElement.TEXT)
                assertNotNull("text ${Color(text).toHex()} on ${Color(page).toHex()}", fixed)
                assertTrue(ContrastAudit.failuresOf(ThemeElement.TEXT, customVisorColors(fixed!!)).isEmpty())
            }
        }
    }

    @Test
    fun customColorsSurviveSavingAndLoading() {
        val custom = CustomColors(ThemeBase.HIGH_CONTRAST_DARK)
            .with(ThemeElement.TEXT, 0xFFFFD400)
            .with(ThemeElement.OUTLINE, 0xFF9CC7FF)
        assertEquals(custom, CustomColors.decode(custom.encode()))
        assertEquals(CustomColors(), CustomColors.decode(CustomColors().encode()))
    }

    @Test
    fun damagedOrUnknownSavedColorsAreSkippedNotFatal() {
        assertEquals(CustomColors(), CustomColors.decode(null))
        assertEquals(CustomColors(), CustomColors.decode(""))
        val decoded = CustomColors.decode("base=NO_SUCH_LOOK;TEXT=zz;SPARKLES=FF000000;BACKGROUND=FF0E2A47")
        assertEquals(ThemeBase.CLARITY_LIGHT, decoded.base)
        assertEquals(mapOf(ThemeElement.BACKGROUND to 0xFF0E2A47.toInt()), decoded.chosen)
    }

    @Test
    fun ownColorsAreUsedOnlyWhileTurnedOn() {
        val custom = CustomColors(ThemeBase.CLARITY_LIGHT).with(ThemeElement.BACKGROUND, 0xFFFFF8E1)
        val off = UserProfile(themeMode = ThemeMode.DARK, appHighContrast = true, customColors = custom)
        assertSameColors("off", VisorColors.of(AppTheme.HighContrastDark), VisorColors.forProfile(off, systemInDarkTheme = false))
        val on = off.copy(useCustomColors = true)
        assertSameColors("on", customVisorColors(custom), VisorColors.forProfile(on, systemInDarkTheme = false))
    }

    /** ColorScheme has no equals of its own, so its colors are compared one by one. */
    private fun assertSameColors(message: String, expected: VisorColors, actual: VisorColors) {
        assertEquals(message, expected.scheme.roles(), actual.scheme.roles())
        assertEquals(message, expected.status, actual.status)
        assertEquals(message, expected.focusRing, actual.focusRing)
        assertEquals(message, expected.isDark, actual.isDark)
    }

    private fun ColorScheme.roles(): Map<String, Color> = mapOf(
        "primary" to primary, "onPrimary" to onPrimary,
        "primaryContainer" to primaryContainer, "onPrimaryContainer" to onPrimaryContainer,
        "inversePrimary" to inversePrimary,
        "secondary" to secondary, "onSecondary" to onSecondary,
        "secondaryContainer" to secondaryContainer, "onSecondaryContainer" to onSecondaryContainer,
        "tertiary" to tertiary, "onTertiary" to onTertiary,
        "tertiaryContainer" to tertiaryContainer, "onTertiaryContainer" to onTertiaryContainer,
        "background" to background, "onBackground" to onBackground,
        "surface" to surface, "onSurface" to onSurface,
        "surfaceVariant" to surfaceVariant, "onSurfaceVariant" to onSurfaceVariant,
        "surfaceTint" to surfaceTint, "inverseSurface" to inverseSurface, "inverseOnSurface" to inverseOnSurface,
        "error" to error, "onError" to onError, "errorContainer" to errorContainer, "onErrorContainer" to onErrorContainer,
        "outline" to outline, "outlineVariant" to outlineVariant, "scrim" to scrim,
        "surfaceBright" to surfaceBright, "surfaceDim" to surfaceDim,
        "surfaceContainer" to surfaceContainer, "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest, "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainerLowest" to surfaceContainerLowest,
    )
}
