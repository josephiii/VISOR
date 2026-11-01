package ucf.visor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import ucf.visor.data.SoundCategory
import ucf.visor.data.VoiceCategory
import ucf.visor.data.Volumes
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
    onToggleSession: () -> Unit = {},
    onCapture: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Each VISOR screen corresponds with a composable and a respective route string.
    NavHost(
        navController = navController,
        // A returning, already-logged-in user starts on "home" directly — see
        // VisorViewModel.startDestination and SessionStore.
        startDestination = viewModel.startDestination
    ) {

        composable("title") {
            TitleScreen(
                viewModel = viewModel,
                onLoginClick = { viewModel.login() },
                onSignUpClick = { viewModel.signUp() },
            )
        }

        composable("login") {
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

        composable("sign_up") {
            SignUpScreen(
                viewModel = viewModel,
                talk = talk,
                // Register only creates the account — the new user must now
                // verify the emailed code.
                onSignUpClick = { email -> viewModel.verifyAccount(email) },
                onLoginClick = { viewModel.login() }
            )
        }

        composable("forgot_password") {
            ForgotPasswordScreen(
                viewModel = viewModel,
                onSendCodeClick = { viewModel.enterCode() },
                onResetPasswordClick = { viewModel.resetPassword() }
            )
        }

        composable("verify_account") {
            VerifyAccountScreen(
                viewModel = viewModel,
                onSendCodeClick = { viewModel.enterCode() }
            )
        }

        composable("reset_password") {
            ResetPasswordScreen(
                viewModel = viewModel,
                onSaveNewPassword = { viewModel.login() }
            )
        }

        composable("enter_code") {
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

        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                talk = talk,
                onToggleSession = onToggleSession,
                onCapture = onCapture,
            )
        }

        composable("hardware_pairing") {
            HardwarePairingScreen(
                viewModel = viewModel,
                talk = talk,
            );
        }

        composable("settings") {
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

        composable("motion_lab") {
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

        composable("help") {
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

        composable("onboarding") {
            ProfileFlowHost(
                viewModel = viewModel,
                speak = talk,
                voiceNav = voiceNav,
                onSetupComplete = { viewModel.home() }
            );
        }

        ///////////////////////////////////////////////////////////////////////
        // PHASE 1
        composable("p1_test_mode_config") {
            Phase1ModeConfigurationScreen(
                viewModel = viewModel,
                talk = talk
            )
        }
    }
}