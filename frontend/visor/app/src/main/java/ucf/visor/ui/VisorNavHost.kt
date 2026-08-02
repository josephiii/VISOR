package ucf.visor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import ucf.visor.ui.screens.auth.EnterCodeScreen
import ucf.visor.ui.screens.auth.ForgotPasswordScreen
import ucf.visor.ui.screens.auth.LoginScreen
import ucf.visor.ui.screens.auth.SignUpScreen
import ucf.visor.ui.screens.home.HomeScreen
import ucf.visor.ui.viewmodel.VisorViewModel

@Composable
fun VisorNavHost(
    navController: NavHostController,
    viewModel: VisorViewModel

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
            )
        }

        composable("enter_code") {
            EnterCodeScreen(
                viewModel = viewModel,
                onCodeComplete = {
                    viewModel.home()
                },
                onResendCodeClick = {
                    // RESEND CODE
                }
            )
        }

//        composable("verify_account") {
//            VerifyAccountScreen()
//        }

        // Add Profile stuff

        composable("home") {
            HomeScreen(
                viewModel = viewModel
            )
        }

//        composable("hardware_connection") {
//            HardwareConnectionScreen();
//        }

    }
}