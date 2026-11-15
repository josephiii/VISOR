package ucf.visor.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ucf.visor.data.ThemeElement

/**
 * Every color pair VISOR draws, in all four themes, against the WCAG contrast
 * targets Section 508 adopts: 4.5:1 for text, and 3:1 for the parts of a
 * control you need to see to use it (WCAG 1.4.11), such as an outline or how
 * far a slider is filled. A color changed in Color.kt that breaks a pair fails
 * here, naming the theme, the pair and its ratio.
 *
 * The pairs themselves are listed once, in ContrastAudit, which the color
 * editor also runs on a user's own colors: the build and the editor can't
 * disagree about what is readable.
 */
class ThemeContrastTest {

    @Test
    fun everyPairInEveryThemeIsReadable() {
        val failures = AppTheme.entries.flatMap { theme ->
            ContrastAudit.failures(VisorColors.of(theme)).map { check ->
                "$theme: ${check.name} is ${"%.2f".format(check.ratio)}:1, needs ${"%.1f".format(check.minimum)}:1"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** The audit covers what the build has always checked, not a quietly shorter list. */
    @Test
    fun theAuditChecksEveryKindOfPair() {
        val names = ContrastAudit.checks(VisorColors.of(AppTheme.ClarityLight)).map { it.name }
        listOf(
            "onSurface on background",
            "onSurfaceVariant on surfaceContainerHigh",
            "primary on surface",
            "error on surfaceDim",
            "onPrimary on primary",
            "onPrimaryContainer on primaryContainer",
            "onSuccess on success",
            "outline against surfaceVariant",
            "focus ring against surfaceBright",
            "notch on the filled track (onPrimary on primary)",
        ).forEach { assertTrue("$it is checked", it in names) }
        assertTrue(names.any { it.startsWith("a selected chip's") })
    }

    @Test
    fun aFaintPairIsCaughtAndNamesItsParts() {
        val faint = VisorColors.of(AppTheme.ClarityLight).let {
            it.copy(scheme = it.scheme.copy(onSurface = Color(0xFFCCCCCC)))
        }
        val failures = ContrastAudit.failuresOf(ThemeElement.TEXT, faint)
        assertFalse(failures.isEmpty())
        assertTrue(failures.all { ThemeElement.TEXT in it.parts })
        // Faintest first.
        assertEquals(failures.minBy { it.ratio / it.minimum }, failures.first())
    }

    @Test
    fun contrastRatioMatchesWcag() {
        assertEquals(21.0, contrastRatio(Color.Black, Color.White), 0.01)
        assertEquals(1.0, contrastRatio(Color(0xFF1A4D98), Color(0xFF1A4D98)), 0.0001)
        // WCAG's own worked example: #767676 on white is just over 4.5:1.
        assertEquals(4.54, contrastRatio(Color(0xFF767676), Color.White), 0.01)
    }
}
