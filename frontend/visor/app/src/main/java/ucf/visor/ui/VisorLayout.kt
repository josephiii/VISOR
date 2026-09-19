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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge
import ucf.visor.BuildConfig
import ucf.visor.ui.components.VisorNavigationBar
import ucf.visor.ui.glasses.GlassesNavigationController
import ucf.visor.ui.glasses.glassesNavScreenFor
import ucf.visor.ui.phase1.Phase1NavigationBar
import ucf.visor.ui.screens.debug.DebugScreen
import ucf.visor.ui.theme.VisorTheme
import ucf.visor.ui.viewmodel.SessionMode
import ucf.visor.ui.viewmodel.VisorViewModel
import ucf.visor.ui.voice.VoiceCommand
import ucf.visor.ui.voice.VoiceNavState
import ucf.visor.ui.voice.VoiceNavigationController


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
    glassesNav: GlassesNavigationController? = null,
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
    LaunchedEffect(uiState.atTitle) {
        if (uiState.atTitle) {
            navController.navigate("title") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Observe LoginScreen
    LaunchedEffect(uiState.isLoggingIn) {
        if (uiState.isLoggingIn) {
            navController.navigate("login") { launchSingleTop = true }
        }
    }

    // Observe SignUpScreen
    LaunchedEffect(uiState.isSigningUp) {
        if (uiState.isSigningUp) {
            navController.navigate("sign_up") { launchSingleTop = true }
        }
    }

    // Observe ForgotPasswordScreen
    LaunchedEffect(uiState.hasForgottenPassword) {
        if (uiState.hasForgottenPassword) {
            navController.navigate("forgot_password") { launchSingleTop = true }
        }
    }

    // Observe EnterCodeScreen
    LaunchedEffect(uiState.isEnteringCode) {
        if (uiState.isEnteringCode) {
            navController.navigate("enter_code") { launchSingleTop = true }
        }
    }

    // Observe VerifyAccountScreen
    LaunchedEffect(uiState.isVerifyingAccount) {
        if (uiState.isVerifyingAccount) {
            navController.navigate("verify_account") { launchSingleTop = true }
        }
    }

    // Observe ResetPasswordScreen
    LaunchedEffect(uiState.isResettingPassword) {
        if (uiState.isResettingPassword) {
            navController.navigate("reset_password") { launchSingleTop = true }
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
            navController.navigate("onboarding") { launchSingleTop = true }
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
    // On-Glasses Tap Navigation Observer
    // Pushes the current screen's button set to the glasses display any time
    // navigation-relevant state changes (route, auth, session, confirm dialogs)
    // — see glassesNavScreenFor. GlassesNavigationController itself no-ops
    // when disabled or no display is attached, so this is safe to call always.
    LaunchedEffect(glassesNav, currentRoute, uiState) {
        glassesNav?.showScreen(glassesNavScreenFor(currentRoute, uiState))
    }

    ///////////////////////////////////////////////////////////////////////////
    // Voice + Glasses-Tap Navigation Observer
    // Both controllers emit the same VoiceCommand type (see its KDoc), so a
    // single merged collector dispatches a spoken command and an on-glasses
    // button tap identically — no glasses-specific cases needed below.
    LaunchedEffect(voiceNav, glassesNav) {
        merge(voiceNav?.commands ?: emptyFlow(), glassesNav?.commands ?: emptyFlow()).collect { command ->
            when (command) {
                // Home/Settings/Help/Pairing/sessions are all post-login-only —
                // none of them have a touch equivalent before uiState.isAuthComplete
                // either (no bottom nav bar yet), so voice shouldn't uniquely
                // unlock them from TitleScreen/Login/SignUp.
                VoiceCommand.GoHome ->
                    if (uiState.isAuthComplete) viewModel.home()
                    else talk("Log in first to go home.")

                VoiceCommand.OpenSettings ->
                    if (uiState.isAuthComplete) viewModel.settings()
                    else talk("Log in first to open settings.")

                VoiceCommand.OpenHelp ->
                    if (uiState.isAuthComplete) viewModel.help()
                    else talk("Log in first for help.")

                VoiceCommand.PairDevice ->
                    if (uiState.isAuthComplete) viewModel.hardwarePairing()
                    else talk("Log in first to pair your glasses.")

                VoiceCommand.GoBack -> {
                    if (!navController.popBackStack()) talk("There's nowhere to go back to.")
                }

                VoiceCommand.ToggleSession -> {
                    if (!uiState.isAuthComplete) {
                        talk("Log in first to start a session.")
                    } else {
                        // Actually starts/ends the session from wherever the command
                        // came from — Settings, Hardware Pairing, or a glasses-tap
                        // fired while the phone is showing any other screen — rather
                        // than just telling the wearer to go find the button
                        // themselves. home() is a safe, idempotent state-setter (see
                        // VoiceCommand.GoHome above) so it's fine to call even when
                        // already on "home".
                        if (currentRoute != "home") viewModel.home()
                        val startingUp = !uiState.isSessionActive
                        viewModel.toggleSession()
                        talk(if (startingUp) "Starting session" else "Ending session")
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

                VoiceCommand.LogOut ->
                    if (uiState.isAuthComplete) viewModel.requestLogoutConfirm()
                    else talk("You're not logged in.")

                VoiceCommand.DeleteAccount ->
                    if (uiState.isAuthComplete) viewModel.requestDeleteAccountConfirm()
                    else talk("You're not logged in.")

                VoiceCommand.ToggleHighContrast ->
                    viewModel.updateProfile(profile.copy(appHighContrast = !profile.appHighContrast))

                is VoiceCommand.SetSpeechRate ->
                    viewModel.updateProfile(profile.copy(speechRate = command.rate))

                is VoiceCommand.SetVerbosity ->
                    viewModel.updateProfile(profile.copy(verbosity = command.verbosity))

                is VoiceCommand.SetTextScale ->
                    viewModel.updateProfile(profile.copy(textScale = command.scale))

                // logout()/confirmDeleteAccount() already clear their own
                // confirm-dialog flag as part of the bigger session reset —
                // no separate cancelXConfirm() call needed here.
                VoiceCommand.Confirm -> when {
                    uiState.isLogoutConfirmVisible -> viewModel.logout()
                    uiState.isDeleteAccountConfirmVisible -> viewModel.confirmDeleteAccount()
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

                        // Exactly one bottom bar, never both: initiating Phase 1
                        // (see DebugScreen's "P1 Initiate" button) also calls
                        // home(), which sets navigationBarEnabled — so without
                        // this being an if/else, both NavigationBars would
                        // render stacked, showing up as extra empty space
                        // (each one pads itself for the bottom system bar
                        // inset) above the real bottom edge of the screen.
                        if (uiState.phase1Initiated) {
                            // FOR PHASE 1
                            Phase1NavigationBar(currentRoute, viewModel)
                        } else if (uiState.navigationBarEnabled) {
                            // Once the user is fully logged in...
                            VisorNavigationBar(currentRoute, viewModel)
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