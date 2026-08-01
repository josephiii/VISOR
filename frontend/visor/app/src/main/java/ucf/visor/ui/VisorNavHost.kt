package ucf.visor.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import ucf.visor.ui.screens.auth.LoginScreen
import ucf.visor.ui.screens.auth.SignUpScreen
import ucf.visor.ui.screens.home.HomeScreen
import ucf.visor.ui.viewmodel.VisorViewModel

@Composable
fun VisorNavHost(
    navController: NavHostController,
    viewModel: VisorViewModel

) {
    // Each VISOR screen corresponds with a composable and a respective route string.
    NavHost(
        navController = navController,
        startDestination = "login"
    ) {

        composable("login") {
            LoginScreen(
                onLoginClick = { _, _ -> navController.navigate("home") },
                onSignUpClick = { navController.navigate("sign_up") }
            )
        }

        composable("sign_up") {
            SignUpScreen()
        }

//        composable("forgot_password") {
//            ForgotPasswordScreen()
//        }

//        composable("enter_code") {
//            EnterCodeScreen()
//        }

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