package ucf.visor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import ucf.visor.BuildConfig
import ucf.visor.ui.components.VisorNavigationBar
import ucf.visor.ui.phase1.Phase1NavigationBar
import ucf.visor.ui.screens.debug.DebugScreen
import ucf.visor.ui.theme.VisorTheme
import ucf.visor.ui.viewmodel.SessionMode
import ucf.visor.ui.viewmodel.VisorViewModel
import ucf.visor.voice.VoiceCommand
import ucf.visor.voice.VoiceNavState
import ucf.visor.voice.VoiceNavigationController


// VisorLayout() will control the application screen state. It calls the separate screen functions
// based on the current viewModel state (uiState) and is where we keep our debugging tools.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisorLayout(
    viewModel: VisorViewModel,
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
    talk: (String) -> Unit = {},
    lastSpoken: () -> String? = { null },
    voiceNav: VoiceNavigationController? = null,
    modifier: Modifier = Modifier,
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val voiceState by (voiceNav?.state ?: remember { MutableStateFlow(VoiceNavState.IDLE) })
        .collectAsStateWithLifecycle()

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
    ///////////////////////////////////////////////////////////////////////////
    // General State Observers:
    // Observe recent errors and show snackbar.
    LaunchedEffect(uiState.recentError) {
        uiState.recentError?.let { errorMessage ->
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.clearRecentError()
        }
    }

    ///////////////////////////////////////////////////////////////////////////
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
        if (uiState.goingHome) { // FIXME: for PHASE 1, remove "&& uiState.isAuthComplete"
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

    // Observe HelpScreen (reachable from Settings, or by voice, from anywhere post-login)
    LaunchedEffect(uiState.atHelp) {
        if (uiState.atHelp) {
            navController.navigate("help") {
                launchSingleTop = true
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                restoreState = true
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Phase 1 Observers

    LaunchedEffect(uiState.isConfiguring) {
        if (uiState.isConfiguring) {
            navController.navigate("p1_test_mode_config") {
                launchSingleTop = true
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                restoreState = true
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Voice Navigation Observer
    //
    // Single dispatch point for every VoiceCommand (see voice/VoiceCommand.kt):
    // this is where both the ViewModel (for the same state-flag navigation every
    // on-screen button already uses) and the NavHostController (for "go back",
    // which has no state-flag equivalent) are both in scope. Commands with no
    // effect in the current context still get a short spoken response — silent
    // no-ops read as "did it even hear me?" to a user who can't see a result.
    LaunchedEffect(voiceNav) {
        voiceNav?.commands?.collect { command ->
            when (command) {
                VoiceCommand.GoHome -> viewModel.home()
                VoiceCommand.OpenSettings -> viewModel.settings()
                VoiceCommand.OpenHelp -> viewModel.help()
                VoiceCommand.PairDevice -> viewModel.hardwarePairing()
                VoiceCommand.GoBack -> {
                    if (!navController.popBackStack()) talk("There's nowhere to go back to.")
                }

                VoiceCommand.ToggleSession -> {
                    if (currentRoute == "home") {
                        val startingUp = !uiState.isSessionActive
                        viewModel.toggleSession()
                        talk(if (startingUp) "Starting session" else "Ending session")
                    } else {
                        talk("Go to Home to start or end a session.")
                    }
                }

                is VoiceCommand.SwitchMode -> {
                    if (uiState.phase1Initiated && uiState.isSessionActive) {
                        viewModel.setMode(command.mode)
                        talk(
                            when (command.mode) {
                                SessionMode.HAZARD -> "Hazard Awareness Mode Activated!"
                                SessionMode.SCENE -> "Scene Description Mode Activated!"
                                SessionMode.READER -> "Reading Assistance Mode Activated!"
                            }
                        )
                    } else {
                        talk("Mode switching isn't available right now.")
                    }
                }

                VoiceCommand.GoToLogin -> viewModel.login()
                VoiceCommand.GoToSignUp -> viewModel.signUp()
                VoiceCommand.GoToForgotPassword -> viewModel.forgotPassword()
                VoiceCommand.ResendCode -> talk("Resend code isn't available yet.")

                VoiceCommand.LogOut -> viewModel.requestLogoutConfirm()
                VoiceCommand.DeleteAccount -> viewModel.requestDeleteAccountConfirm()

                VoiceCommand.ToggleHighContrast ->
                    viewModel.updateProfile(profile.copy(appHighContrast = !profile.appHighContrast))

                is VoiceCommand.SetSpeechRate ->
                    viewModel.updateProfile(profile.copy(speechRate = command.rate))

                is VoiceCommand.SetVerbosity ->
                    viewModel.updateProfile(profile.copy(verbosity = command.verbosity))

                is VoiceCommand.SetTextScale ->
                    viewModel.updateProfile(profile.copy(textScale = command.scale))

                VoiceCommand.Confirm -> when {
                    uiState.isLogoutConfirmVisible -> {
                        viewModel.cancelLogoutConfirm(); viewModel.login()
                    }

                    uiState.isDeleteAccountConfirmVisible -> viewModel.cancelDeleteAccountConfirm()
                    else -> Unit
                }

                VoiceCommand.Cancel -> when {
                    uiState.isLogoutConfirmVisible -> viewModel.cancelLogoutConfirm()
                    uiState.isDeleteAccountConfirmVisible -> viewModel.cancelDeleteAccountConfirm()
                    else -> Unit
                }

                // "Next"/"repeat" only mean something inside the onboarding wizard's
                // own voice-first flow, which listens directly (no "VISOR GO" needed)
                // and doesn't go through this dispatcher — see ProfileCreationScreen.
                VoiceCommand.Next -> talk("There's nothing to move to next here.")
                VoiceCommand.RepeatLast -> talk(lastSpoken() ?: "I haven't said anything yet.")

                VoiceCommand.Unrecognized -> Unit // already told the user via VoiceNavigationController
            }
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
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
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
                if (BuildConfig.DEBUG && !uiState.phase1Initiated) {
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
                // Box, not just the Column below, so the voice status pop-up can
                // float on top of the screen instead of taking up its own row —
                // a row here would shift every screen's content down each time
                // voice nav starts/stops listening.
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Sets the routes for the screens and is where the observers direct their uiState traffic
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(innerPadding)
                        ) {
                            VisorNavHost(
                                navController = navController,
                                viewModel = viewModel,
                                talk = talk,
                                voiceNav = voiceNav,
                            )
                        }

                        // Once the user is fully logged in...
                        if (uiState.navigationBarEnabled) {
                            // Bottom Navigation Bar for VISOR
                            VisorNavigationBar(currentRoute, viewModel)
                        }

                        // FOR PHASE 1
                        if (uiState.phase1Initiated) {
                            Phase1NavigationBar(currentRoute, viewModel)
                        }
                    }

                    // Voice navigation status pop-up. Floats over the content
                    // (doesn't reflow it) and sits below statusBarsPadding() so
                    // it never covers the status bar / notification-shade pull
                    // handle, even though the app otherwise draws edge-to-edge.
                    // A `liveRegion` so a screen reader announces it as it
                    // appears/changes, since a user relying on voice nav may
                    // not be looking at the screen to see it.
                    AnimatedVisibility(
                        visible = voiceState != VoiceNavState.IDLE,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 12.dp),
                        enter = fadeIn() + slideInVertically { -it },
                        exit = fadeOut() + slideOutVertically { -it },
                    ) {
                        Surface(
                            shape = CutCornerShape(35),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shadowElevation = 6.dp,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (voiceState == VoiceNavState.LISTENING)
                                        "Listening for a command…"
                                    else
                                        "Working on it…",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
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