package ucf.visor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import ucf.visor.ui.phase1.Phase1ModeConfigurationScreen
import ucf.visor.ui.profile.ProfileFlowHost
import ucf.visor.ui.screens.auth.EnterCodeScreen
import ucf.visor.ui.screens.auth.ForgotPasswordScreen
import ucf.visor.ui.screens.auth.LoginScreen
import ucf.visor.ui.screens.auth.ResetPasswordScreen
import ucf.visor.ui.screens.auth.SignUpScreen
import ucf.visor.ui.screens.auth.VerifyAccountScreen
import ucf.visor.ui.screens.hardware.HardwarePairingScreen
import ucf.visor.ui.screens.help.HelpScreen
import ucf.visor.ui.screens.home.HomeScreen
import ucf.visor.ui.screens.profile.SettingsScreen
import ucf.visor.ui.viewmodel.VisorViewModel

@Composable
fun VisorNavHost(
    navController: NavHostController,
    viewModel: VisorViewModel,
    talk: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Each VISOR screen corresponds with a composable and a respective route string.
    NavHost(
        navController = navController,
        startDestination = "login"
    ) {

        composable("login") {
            LoginScreen(
                viewModel = viewModel,
                onLoginClick = { viewModel.verifyAccount() },
                onForgotPasswordClick = { viewModel.forgotPassword() },
                onSignUpClick = { viewModel.signUp() }
            )
        }

        composable("sign_up") {
            SignUpScreen(
                viewModel = viewModel,
                onSignUpClick = { viewModel.verifyAccount() },
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
            EnterCodeScreen(
                viewModel = viewModel,
                onCodeComplete = {
                    val lastRoute = navController.previousBackStackEntry?.destination?.route
                    if (lastRoute == "forgot_password")
                        viewModel.home()
                    else // == "verify_account"
                        viewModel.onboard()
                },
                onResendCodeClick = {
                    // TODO: RESEND CODE LOGIC
                }
            )
        }

        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                talk = talk
            )
        }

        composable("hardware_pairing") {
            HardwarePairingScreen(
                viewModel = viewModel
            );
        }

        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onProfileChange = { viewModel.updateProfile(it) },
                onLogout = { viewModel.login() },
                // onDeleteAccount: TODO wire to POST /auth/deleteAccount once Joseph's endpoint lands.
                onHelp = { navController.navigate("help") },
            );
        }

        composable("help") {
            HelpScreen(onBack = { navController.popBackStack() })
        }

        composable("onboarding") {
            ProfileFlowHost( // TODO: Add speak function parameter
                viewModel = viewModel,
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