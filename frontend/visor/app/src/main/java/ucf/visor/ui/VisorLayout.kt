package ucf.visor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import ucf.visor.BuildConfig
import ucf.visor.R
import ucf.visor.ui.screens.debug.DebugScreen
import ucf.visor.ui.theme.VisorTheme
import ucf.visor.ui.viewmodel.VisorViewModel

// VisorLayout() will control the application screen state. It calls the separate screen functions
// based on the current viewModel state (uiState) and is where we keep our debugging tools.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisorLayout(
    viewModel: VisorViewModel,
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
    modifier: Modifier = Modifier,
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Used to display errors. Pass errors strings through uiState.recentError to be displayed through the snackbar.
    val snackbarHostState =
        remember { SnackbarHostState() }

    // DEBUG TOOL: Mock Device Kit Debug button
    val bottomSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Controls screen state (see VisorNavHost)
    val navController = rememberNavController()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // OBSERVERS
    // General State Observers:
    // Observe recent errors and show snackbar.
    LaunchedEffect(uiState.recentError) {
        uiState.recentError?.let { errorMessage ->
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.clearRecentError()
        }
    }

    // Screen State Observers:
    // Observe LoginScreen
    LaunchedEffect(uiState.isLoggingIn) {
        if (uiState.isLoggingIn) {
            navController.navigate("login")
        }
    }

    // Observe SignUpScreen
    LaunchedEffect(uiState.isSigningUp) {
        if (uiState.isSigningUp) {
            navController.navigate("sign_up")
        }
    }

    // Observe ForgotPasswordScreen
    LaunchedEffect(uiState.hasForgottenPassword) {
        if (uiState.hasForgottenPassword) {
            navController.navigate("forgot_password")
        }
    }

    // Observe EnterCodeScreen
    LaunchedEffect(uiState.isEnteringCode) {
        if (uiState.isEnteringCode) {
            navController.navigate("enter_code")
        }
    }

    // Observe VerifyAccountScreen
    LaunchedEffect(uiState.isVerifyingAccount) {
        if (uiState.isVerifyingAccount) {
            navController.navigate("verify_account")
        }
    }

    // Observe ResetPasswordScreen
    LaunchedEffect(uiState.isResettingPassword) {
        if (uiState.isResettingPassword) {
            navController.navigate("reset_password")
        }
    }

    // Observe HomeScreen
    LaunchedEffect(uiState.goingHome) {
        if (uiState.goingHome && uiState.isAuthComplete) {
            navController.navigate("home") {
                launchSingleTop = true
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                restoreState = true
            }
        }
    }

    // Observe HardwarePairingScreen
    LaunchedEffect(uiState.isPairingHardware) {
        if (uiState.isPairingHardware) {
            navController.navigate("hardware_pairing") {
                launchSingleTop = true
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                restoreState = true
            }
        }
    }

    // Observe SettingsScreen
    LaunchedEffect(uiState.atSettings) {
        if (uiState.atSettings) {
            navController.navigate("settings") {
                launchSingleTop = true
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                restoreState = true
            }
        }
    }

    // Observe Onboarding Process
    LaunchedEffect(uiState.isOnboarding) {
        if (uiState.isOnboarding) {
            navController.navigate("onboarding")
        }
    }

    // This is the Active Screen Surface!
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            modifier = modifier,
            snackbarHost = {
                // Error logging snackbar.
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier =
                        Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 32.dp),
                    snackbar = { data ->
                        Snackbar(
                            shape = RoundedCornerShape(24.dp),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Camera Access error",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(data.visuals.message)
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                // DEBUG TOOLS: includes MockDeviceKit and debug screen viewer.
                if (BuildConfig.DEBUG) {
                    FloatingActionButton(
                        onClick = { viewModel.showDebugMenu() },
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = "Debug Menu")
                    }

                    if (uiState.isDebugMenuVisible) {
                        ModalBottomSheet(
                            onDismissRequest = { viewModel.hideDebugMenu() },
                            sheetState = bottomSheetState,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            DebugScreen(
                                visorViewModel = viewModel,
                                navController = navController,
                                onDismiss = { viewModel.hideDebugMenu() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            },
            content = { innerPadding ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Sets the routes for the screens and is where the observers direct their uiState traffic
                    Box(modifier = Modifier.weight(1f)) {
                        VisorNavHost(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }

                    // Once the user is fully logged in and directed home.
                    if (uiState.isAuthComplete) {
                        // Bottom Navigation Bar
                        NavigationBar(
                            modifier = Modifier.fillMaxWidth(),
                            contentColor = MaterialTheme.colorScheme.contentColorFor(
                                NavigationBarDefaults.containerColor
                            )
                        ) {
                            NavigationBarItem(
                                selected = currentRoute == "home",
                                onClick = { viewModel.home() },
                                icon = {
                                    Icon(
                                        Icons.Default.Home,
                                        contentDescription = "Home"
                                    )
                                },
                                label = { Text(stringResource(R.string.home_screen_title)) }

                            )
                            NavigationBarItem(
                                selected = currentRoute == "settings",
                                onClick = { viewModel.settings() },
                                icon = {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = "Profile Settings"
                                    )
                                },
                                label = { Text(stringResource(R.string.user_profile_navbar_title)) }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "hardware_pairing",
                                onClick = { viewModel.hardwarePairing() },
                                icon = {
                                    Icon(
                                        Icons.Default.Bluetooth,
                                        contentDescription = "Hardware"
                                    )
                                },
                                label = { Text(stringResource(R.string.hardware_pairing_navbar_title)) }
                            )
                        }
                    }
                }
            }
        )
    }
}

///////////////////////////////////////////////////////////////////////////////
// Insert the component here to view it with Visor Themes applied.
// VisorThemes are applied at the top level in MainActivity.kt
@Preview
@Composable
fun PreviewWithTheme() {
    VisorTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // Place Preview-Composable functions here.
            //...
        }
    }
}