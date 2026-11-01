package ucf.visor.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ucf.visor.data.TextScale
import ucf.visor.ui.components.VisorNavigationBar
import ucf.visor.ui.screens.auth.ForgotPasswordScreen
import ucf.visor.ui.screens.auth.LoginScreen
import ucf.visor.ui.screens.auth.SignUpScreen
import ucf.visor.ui.screens.hardware.HardwarePairingScreen
import ucf.visor.ui.screens.help.HelpScreen
import ucf.visor.ui.screens.home.HomeScreen
import ucf.visor.ui.screens.phase1.Phase1ModeConfigurationScreen
import ucf.visor.ui.screens.profile.ProfileCreationScreen
import ucf.visor.ui.screens.profile.SettingsScreen
import ucf.visor.ui.screens.title.TitleScreen
import ucf.visor.ui.theme.AppTheme
import ucf.visor.ui.theme.VisorTheme
import ucf.visor.ui.viewmodel.SettingsSection
import ucf.visor.ui.viewmodel.VisorViewModel

/**
 * VISOR's screens at the largest text a low-vision user is likely to set:
 * Android's font size at 200%, on top of VISOR's own Extra large, on the
 * walkthrough's phone (1080 × 2400 at 420 dpi, so 411 × 914 dp). No label may
 * be cut off (WCAG 1.4.4): one that doesn't fit has to wrap. Labels used to
 * shrink and then clip instead, as "Forgot Passwor" and "SIGN UI".
 *
 * The font scale here is applied evenly, so large text grows a full 2x;
 * Android 14 and up grows large text a little less, so a phone has more room
 * than this test does.
 */
@RunWith(AndroidJUnit4::class)
class LargeTextTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun title() = assertNothingCutOff {
        TitleScreen(viewModel = VisorViewModel(app), onLoginClick = {}, onSignUpClick = {})
    }

    @Test
    fun login() = assertNothingCutOff {
        LoginScreen(
            viewModel = VisorViewModel(app),
            onLoginSuccess = { _, _ -> },
            onForgotPasswordClick = {},
            onSignUpClick = {},
            onUnverified = {},
        )
    }

    @Test
    fun signUp() = assertNothingCutOff {
        SignUpScreen(viewModel = VisorViewModel(app), onSignUpClick = {}, onLoginClick = {})
    }

    @Test
    fun forgotPassword() = assertNothingCutOff {
        ForgotPasswordScreen(viewModel = VisorViewModel(app), onSendCodeClick = {}, onResetPasswordClick = {})
    }

    @Test
    fun home() = assertNothingCutOff { HomeScreen(viewModel = VisorViewModel(app)) }

    @Test
    fun settingsHub() = assertNothingCutOff { SettingsScreen(viewModel = VisorViewModel(app)) }

    @Test
    fun settingsAboutYou() = settingsPart(SettingsSection.ABOUT_YOU)

    @Test
    fun settingsSpeech() = settingsPart(SettingsSection.SPEECH)

    @Test
    fun settingsSound() = settingsPart(SettingsSection.SOUND)

    @Test
    fun settingsDisplay() = settingsPart(SettingsSection.DISPLAY)

    @Test
    fun settingsCommands() = settingsPart(SettingsSection.COMMANDS)

    @Test
    fun settingsAccount() = settingsPart(SettingsSection.ACCOUNT)

    private fun settingsPart(section: SettingsSection) {
        val viewModel = VisorViewModel(app).apply { openSettingsSection(section) }
        assertNothingCutOff { SettingsScreen(viewModel = viewModel) }
    }

    @Test
    fun help() = assertNothingCutOff { HelpScreen(onBack = {}) }

    @Test
    fun onboarding() = assertNothingCutOff { ProfileCreationScreen(viewModel = VisorViewModel(app)) }

    @Test
    fun pairGlasses() = assertNothingCutOff { HardwarePairingScreen(viewModel = VisorViewModel(app)) }

    @Test
    fun phase1() = assertNothingCutOff { Phase1ModeConfigurationScreen(viewModel = VisorViewModel(app)) }

    @Test
    fun navigationBar() = assertNothingCutOff {
        VisorNavigationBar(currentRoute = "home", viewModel = VisorViewModel(app))
    }

    private fun assertNothingCutOff(screen: @Composable () -> Unit) {
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                VisorTheme(appTheme = AppTheme.ClarityLight, textScale = TextScale.EXTRA_LARGE.multiplier) {
                    Surface {
                        Box(modifier = Modifier.size(width = 411.dp, height = 914.dp)) { screen() }
                    }
                }
            }
        }
        rule.waitForIdle()

        // Every piece of text on the screen, scrolled into view or not: the
        // screens scroll a Column, so all of it is laid out.
        val problems = rule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                layouts.mapNotNull { it.problem() }
            }
        assertTrue("At 200% text with Extra large: $problems", problems.isEmpty())
    }

    /**
     * What is wrong with how this text fits, or null if nothing is. Not
     * [TextLayoutResult.hasVisualOverflow]: a one-line label is laid out across
     * all the width it may take but sized to its words, which that counts as
     * overflowing although every letter shows.
     */
    private fun TextLayoutResult.problem(): String? {
        val text = layoutInput.text.text
        val lastLine = lineCount - 1
        return when {
            didOverflowHeight -> "\"$text\" is taller than its space, so its last lines are cut off"
            isLineEllipsized(lastLine) -> "\"$text\" is cut short with an ellipsis"
            !layoutInput.softWrap && didOverflowWidth -> "\"$text\" is wider than its space and can't wrap"
            // Wrapping in the middle of a word makes it much harder to read
            // than wrapping between words.
            (0 until lastLine).any { line ->
                val end = getLineEnd(line, visibleEnd = true)
                end in 1 until text.length && text[end - 1].isLetter() && text[end].isLetter()
            } -> "\"$text\" breaks in the middle of a word"
            else -> null
        }
    }
}
