package ucf.visor.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import ucf.visor.R
import ucf.visor.data.SoundCategory
import ucf.visor.data.VoiceCategory
import ucf.visor.data.Volumes
import ucf.visor.stt.CameraCommand
import ucf.visor.ui.screens.phase1.Phase1ModeConfigurationScreen
import ucf.visor.ui.screens.profile.ProfileFlowHost
import ucf.visor.ui.screens.auth.EnterCodeScreen
import ucf.visor.ui.screens.auth.ForgotPasswordScreen
import ucf.visor.ui.screens.auth.LoginScreen
import ucf.visor.ui.screens.auth.ResetPasswordScreen
import ucf.visor.ui.screens.auth.SignUpScreen
import ucf.visor.ui.screens.auth.VerifyAccountScreen
import ucf.visor.ui.screens.hardware.HardwarePairingScreen
import ucf.visor.ui.screens.help.HelpScreen
import ucf.visor.ui.screens.home.HomeScreen
import ucf.visor.ui.screens.motionlab.MotionLabScreen
import ucf.visor.ui.screens.profile.SettingsScreen
import ucf.visor.ui.screens.title.TitleScreen
import ucf.visor.ui.viewmodel.VisorViewModel
import ucf.visor.ui.voice.VoiceNavigationController

@Composable
fun VisorNavHost(
    navController: NavHostController,
    viewModel: VisorViewModel,
    talk: (String, VoiceCategory) -> Unit = { _, _ -> },
    speakAt: (String, Float) -> Unit = { _, _ -> },
    playSample: (SoundCategory) -> Unit = {},
    voiceNav: VoiceNavigationController? = null,
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus = { PermissionStatus.Denied },
    onCapture: (CameraCommand) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Each VISOR screen corresponds with a composable and a respective route string.
    NavHost(
        navController = navController,
        // A returning, already-logged-in user starts on "home" directly — see
        // VisorViewModel.startDestination and SessionStore.
        startDestination = viewModel.startDestination
    ) {

        screen("title", R.string.app_name) {
            TitleScreen(
                viewModel = viewModel,
                onLoginClick = { viewModel.login() },
                onSignUpClick = { viewModel.signUp() },
            )
        }

        screen("login", R.string.login_title) {
            LoginScreen(
                viewModel = viewModel,
                talk = talk,
                // On a successful login the ViewModel persists tokens + goes home.
                onLoginSuccess = { access, refresh -> viewModel.onLoginSuccess(access, refresh) },
                onForgotPasswordClick = { viewModel.forgotPassword() },
                onSignUpClick = { viewModel.signUp() },
                onUnverified = { email -> viewModel.verifyAccount(email) }
            )
        }

        screen("sign_up", R.string.sign_up_title) {
            SignUpScreen(
                viewModel = viewModel,
                talk = talk,
                // Register only creates the account — the new user must now
                // verify the emailed code.
                onSignUpClick = { email -> viewModel.verifyAccount(email) },
                onLoginClick = { viewModel.login() }
            )
        }

        screen("forgot_password", R.string.forgot_password_title) {
            ForgotPasswordScreen(
                viewModel = viewModel,
                onSendCodeClick = { viewModel.enterCode() },
                onResetPasswordClick = { viewModel.resetPassword() }
            )
        }

        screen("verify_account", R.string.verify_account_title) {
            VerifyAccountScreen(
                viewModel = viewModel,
                onSendCodeClick = { viewModel.enterCode() }
            )
        }

        screen("reset_password", R.string.reset_password_title) {
            ResetPasswordScreen(
                viewModel = viewModel,
                onSaveNewPassword = { viewModel.login() }
            )
        }

        screen("enter_code", R.string.enter_code_title) {
            // enter_code is reached from verify_account (email verification) or
            // forgot_password; only the former checks the code with the backend.
            val fromVerify = remember {
                navController.previousBackStackEntry?.destination?.route == "verify_account"
            }
            EnterCodeScreen(
                viewModel = viewModel,
                verifyEmail = if (fromVerify) uiState.pendingEmail else null,
                onVerified = { access, refresh -> viewModel.onEmailVerified(access, refresh) },
                onCodeComplete = { viewModel.home() },
                onResendCodeClick = {
                    // TODO: RESEND CODE LOGIC
                }
            )
        }

        screen("home", R.string.home_screen_title) {
            HomeScreen(
                viewModel = viewModel,
                talk = talk,
                onCapture = onCapture,
            )
        }

        screen("hardware_pairing", R.string.hardware_pairing_title) {
            HardwarePairingScreen(
                viewModel = viewModel,
                talk = talk,
            );
        }

        screen("settings", R.string.settings_title) {
            SettingsScreen(
                viewModel = viewModel,
                talk = talk,
                speakAt = speakAt,
                playSample = playSample,
                onProfileChange = { viewModel.updateProfile(it) },
                onLogout = { viewModel.logout() },
                onDeleteAccount = { viewModel.confirmDeleteAccount() },
                onHelp = { viewModel.help() },
            );
        }

        screen("motion_lab", R.string.motion_lab_title) {
            MotionLabScreen(
                wearablesReady = uiState.canRegister,
                // The lab's spoken cues are part of its research protocol, like
                // its tones (motionlab/CuePlayer), so VISOR's volume sliders
                // don't apply to them. The phone's volume buttons still do.
                talk = { speakAt(it, Volumes.Max) },
                onRequestWearablesPermission = onRequestWearablesPermission,
                onPairGlasses = { viewModel.hardwarePairing() },
            )
        }

        screen("help", R.string.help_title) {
            HelpScreen(
                talk = talk,
                // Back to Settings, which Help belongs to (its tab stays
                // selected here), not to the screen before Settings.
                onBack = {
                    viewModel.closeHelp()
                    viewModel.settings()
                },
            )
        }

        screen("onboarding", R.string.onboarding_title) {
            ProfileFlowHost(
                viewModel = viewModel,
                speak = talk,
                voiceNav = voiceNav,
                onSetupComplete = { viewModel.home() }
            );
        }

        ///////////////////////////////////////////////////////////////////////
        // PHASE 1
        screen("p1_test_mode_config", R.string.p1_mode_config_screen_title) {
            Phase1ModeConfigurationScreen(
                viewModel = viewModel,
                talk = talk
            )
        }
    }
}

/**
 * One of VISOR's screens at [route], named [title] for accessibility services:
 * a screen reader such as TalkBack says the name as the screen appears, so a
 * user knows where they are, however they got there (a tap, a voice command,
 * or a button on the glasses).
 */
private fun NavGraphBuilder.screen(
    route: String,
    @StringRes title: Int,
    content: @Composable () -> Unit,
) = composable(route) {
    val name = stringResource(title)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { paneTitle = name },
    ) {
        content()
    }
}
