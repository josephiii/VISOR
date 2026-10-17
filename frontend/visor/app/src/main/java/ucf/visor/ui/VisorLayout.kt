package ucf.visor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Error
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import ucf.visor.BuildConfig
import ucf.visor.ui.components.VisorNavigationBar
import ucf.visor.ui.glasses.GlassesNavigationController
import ucf.visor.ui.glasses.glassesNavScreenFor
import ucf.visor.ui.screens.phase1.Phase1NavigationBar
import ucf.visor.data.SpeechRates
import ucf.visor.tts.SpokenPhrases
import ucf.visor.ui.screens.debug.DebugScreen
import ucf.visor.ui.theme.VisorTheme
import ucf.visor.ui.viewmodel.VisorViewModel
import ucf.visor.ui.voice.VoiceCommand
import ucf.visor.ui.voice.VoiceListeningGlow
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
    beep: () -> Unit = {},
    lastSpoken: () -> String? = { null },
    onCapture: () -> Unit = {},
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

    // Voice navigation listening, felt as well as seen (see VoiceListeningGlow
    // below), for a user who isn't looking at the screen: a firm buzz as the
    // microphone opens for a command, and a light tick as it closes. Not
    // ToggleOn for the buzz: before Android 14 that falls back to a faint click.
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(voiceNav, haptics) {
        voiceNav?.state
            ?.map { it == VoiceNavState.LISTENING }
            ?.distinctUntilChanged()
            ?.drop(1) // the state at launch is not a change
            ?.collect { listening ->
                haptics.performHapticFeedback(
                    if (listening) HapticFeedbackType.LongPress else HapticFeedbackType.ToggleOff
                )
            }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Screen State Observers:
    LaunchedEffect(uiState.atTitle) {
        if (uiState.atTitle) {
            navController.navigate("title") {
                popUpTo(0) { inclusive = true }
            }
            viewModel.onNavigationHandled()
        }
    }

    // Observe LoginScreen
    LaunchedEffect(uiState.isLoggingIn) {
        if (uiState.isLoggingIn) {
            navController.navigate("login") { launchSingleTop = true }
            viewModel.onNavigationHandled()
        }
    }

    // Observe SignUpScreen
    LaunchedEffect(uiState.isSigningUp) {
        if (uiState.isSigningUp) {
            navController.navigate("sign_up") { launchSingleTop = true }
            viewModel.onNavigationHandled()
        }
    }

    // Observe ForgotPasswordScreen
    LaunchedEffect(uiState.hasForgottenPassword) {
        if (uiState.hasForgottenPassword) {
            navController.navigate("forgot_password") { launchSingleTop = true }
            viewModel.onNavigationHandled()
        }
    }

    // Observe EnterCodeScreen
    LaunchedEffect(uiState.isEnteringCode) {
        if (uiState.isEnteringCode) {
            navController.navigate("enter_code") { launchSingleTop = true }
            viewModel.onNavigationHandled()
        }
    }

    // Observe VerifyAccountScreen
    // DISABLED: email verification is cut for now — nothing sets
    // isVerifyingAccount, so this observer is dead. Restore when email
    // verification is added back (see VisorViewModel.verifyAccount()).
    // LaunchedEffect(uiState.isVerifyingAccount) {
    //     if (uiState.isVerifyingAccount) {
    //         navController.navigate("verify_account") { launchSingleTop = true }
    //     }
    // }

    // Observe ResetPasswordScreen
    LaunchedEffect(uiState.isResettingPassword) {
        if (uiState.isResettingPassword) {
            navController.navigate("reset_password") { launchSingleTop = true }
            viewModel.onNavigationHandled()
        }
    }

    // Observe HomeScreen
    LaunchedEffect(uiState.goingHome) {
        if (uiState.goingHome) { // FIXME: for PHASE 1, remove "&& uiState.isAuthComplete"
            navController.navigate("home") {
                launchSingleTop = true
                if (uiState.phase1Initiated) {
                    popUpTo(0) { inclusive = true }
                } else {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    restoreState = true
                }
            }
            viewModel.onNavigationHandled()
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
            viewModel.onNavigationHandled()
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
            viewModel.onNavigationHandled()
        }
    }

    // Observe Onboarding Process
    LaunchedEffect(uiState.isOnboarding) {
        if (uiState.isOnboarding) {
            navController.navigate("onboarding") { launchSingleTop = true }
            viewModel.onNavigationHandled()
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
            viewModel.onNavigationHandled()
        }
    }

    // Observe MotionLabScreen (Debug menu → Test Suite)
    LaunchedEffect(uiState.atMotionLab) {
        if (uiState.atMotionLab) {
            navController.navigate("motion_lab") {
                launchSingleTop = true
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                restoreState = true
            }
            viewModel.onNavigationHandled()
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
            viewModel.onNavigationHandled()
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // On-Glasses Tap Navigation Observer
    LaunchedEffect(glassesNav, currentRoute, uiState) {
        glassesNav?.showScreen(glassesNavScreenFor(currentRoute, uiState))
    }

    ///////////////////////////////////////////////////////////////////////////
    // Voice + Glasses-Tap Navigation Observer
    LaunchedEffect(voiceNav, glassesNav) {
        merge(
            voiceNav?.commands ?: emptyFlow(),
            glassesNav?.commands ?: emptyFlow()
        ).collect { command ->
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
                        // Read the route now: currentRoute is from the composition that started this collector.
                        if (navController.currentDestination?.route != "home") viewModel.home()
                        viewModel.toggleSession(onRequestWearablesPermission, talk)
                    }
                }

                // Modes pick what Home's capture button does, so they switch
                // whenever a session is running, not only in Phase 1.
                is VoiceCommand.SwitchMode -> {
                    if (uiState.isSessionActive) {
                        viewModel.setMode(command.mode)
                        talk(SpokenPhrases.modeActivated(command.mode))
                    } else {
                        talk(SpokenPhrases.START_SESSION_TO_SWITCH_MODES)
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

                VoiceCommand.SpeakFaster -> {
                    val rate = SpeechRates.faster(profile.speechRate)
                    viewModel.updateProfile(profile.copy(speechRate = rate))
                    talk("Faster. ${SpeechRates.spokenLabel(rate)}.")
                }

                VoiceCommand.SpeakSlower -> {
                    val rate = SpeechRates.slower(profile.speechRate)
                    viewModel.updateProfile(profile.copy(speechRate = rate))
                    talk("Slower. ${SpeechRates.spokenLabel(rate)}.")
                }

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
        // A Box so the voice listening glow can draw over everything, the
        // navigation bar included, without taking up any room of its own.
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = modifier,
                // The navigation bar goes in the Scaffold's bottom bar slot, so the
                // Scaffold pads the screen above it by exactly the bar's height.
                //
                // It used to sit in a Column under the screen, with the Scaffold's
                // padding (which ends with the system navigation bar's height) on
                // the screen. NavigationBar also pads itself for the system bar, so
                // that height was added twice: the second time as a blank band
                // between each screen and the navigation bar.
                bottomBar = {
                    // Exactly one bottom bar, never both: initiating Phase 1 (see
                    // DebugScreen's "P1 Initiate" button) also calls home(), which
                    // sets navigationBarEnabled.
                    if (uiState.phase1Initiated) {
                        // FOR PHASE 1
                        Phase1NavigationBar(currentRoute, viewModel)
                    } else if (uiState.navigationBarEnabled) {
                        // Once the user is fully logged in...
                        VisorNavigationBar(currentRoute, viewModel)
                    }
                },
                snackbarHost = {
                    // Error logging snackbar. No navigationBarsPadding(): the
                    // Scaffold already places it above the navigation bar, or above
                    // the system bar when there is no navigation bar.
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 32.dp),
                        snackbar = { data ->
                            Snackbar(
                                shape = RoundedCornerShape(24.dp),
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = "Error",
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
                    // The debug menu can hide this button until VISOR restarts.
                    if (BuildConfig.DEBUG && !uiState.phase1Initiated && !uiState.isDebugButtonHidden) {
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
                    // Sets the routes for the screens and is where the observers direct their uiState traffic
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            // The system bars are taken care of here, so a
                            // navigationBarsPadding() inside a screen can't add
                            // the gap above the navigation bar back.
                            .consumeWindowInsets(innerPadding)
                    ) {
                        VisorNavHost(
                            navController = navController,
                            viewModel = viewModel,
                            talk = talk,
                            beep = beep,
                            voiceNav = voiceNav,
                            onRequestWearablesPermission = onRequestWearablesPermission,
                            onCapture = onCapture,
                        )
                    }
                }
            )

            // Voice navigation is listening: the screen's edges glow, instead of a
            // pop-up over the top of the screen, where it covered headings. Not a
            // screen reader live region, as the pop-up was: VISOR already says
            // "Listening." itself, and a screen reader saying more as the
            // microphone opened could be heard as part of the command.
            VoiceListeningGlow(state = voiceState, modifier = Modifier.matchParentSize())
        }
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