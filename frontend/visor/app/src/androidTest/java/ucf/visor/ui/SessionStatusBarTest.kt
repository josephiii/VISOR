package ucf.visor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ucf.visor.data.TextScale
import ucf.visor.ui.components.SessionStatusBar
import ucf.visor.ui.theme.AppTheme
import ucf.visor.ui.theme.VisorTheme
import ucf.visor.ui.viewmodel.SessionMode

/**
 * The session status bar is one line tall whatever the text size, on or off,
 * in every mode: on the walkthrough's phone (411 dp wide) at VISOR's Extra
 * large, with Android's font size at 100% and at 200%. It shortens its words,
 * and then shows the mode's icon alone, rather than wrap.
 */
@RunWith(AndroidJUnit4::class)
class SessionStatusBarTest {

    @get:Rule
    val rule = createComposeRule()

    /** One way to show the bar: the text size, and whether a session runs in which mode. */
    private data class Case(val fontScale: Float, val active: Boolean, val mode: SessionMode)

    @Test
    fun oneLineAtEveryTextSize() {
        val cases = listOf(1f, 2f).flatMap { fontScale ->
            listOf(false, true).flatMap { active ->
                SessionMode.entries.map { mode -> Case(fontScale, active, mode) }
            }
        }
        // setContent may only be called once, so each case is shown in turn.
        var case by mutableStateOf(cases.first())
        var lineHeight = 0.dp
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, case.fontScale)) {
                VisorTheme(appTheme = AppTheme.ClarityLight, textScale = TextScale.EXTRA_LARGE.multiplier) {
                    lineHeight = with(LocalDensity.current) { MaterialTheme.typography.titleMedium.lineHeight.toDp() }
                    Surface {
                        Box(modifier = Modifier.width(411.dp)) {
                            SessionStatusBar(active = case.active, mode = case.mode, onClick = {})
                        }
                    }
                }
            }
        }

        cases.forEach { next ->
            case = next
            rule.waitForIdle()
            val bar = rule
                .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
                .fetchSemanticsNode()
            val height = with(rule.density) { bar.size.height.toDp() }
            // One line, plus the bar's 8 dp padding above and below it. Two
            // lines would be a whole line taller.
            assertTrue(
                "At font scale ${next.fontScale}, ${if (next.active) "on" else "off"} in ${next.mode}: " +
                    "the bar is $height tall, for a line $lineHeight tall",
                height < lineHeight * 1.5f + 16.dp,
            )
        }
    }
}
