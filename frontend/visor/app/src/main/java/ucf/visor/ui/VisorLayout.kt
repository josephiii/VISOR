package ucf.visor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
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
import ucf.visor.R
import ucf.visor.stt.CameraCommand
import androidx.compose.ui.unit.dp
import ucf.visor.ui.components.ConnectivityNotice
import ucf.visor.ui.components.ErrorBanner
import ucf.visor.ui.components.ReadingControlsBar
import ucf.visor.ui.components.VisorNavigationBar
import ucf.visor.ui.components.isScreenReaderOn
import ucf.visor.ui.feedback.LocalHaptics
import ucf.visor.ui.glasses.GlassesNavigationController
import ucf.visor.ui.glasses.glassesNavScreenFor
import ucf.visor.data.CameraChoice
import ucf.visor.data.SoundCategory
import ucf.visor.data.SpeechRates
import ucf.visor.data.VoiceCategory
import ucf.visor.tts.ReadingControls
import ucf.visor.tts.ReadingState
import ucf.visor.tts.Speaker
import ucf.visor.tts.SpokenPhrases
import ucf.visor.ui.screens.debug.DebugScreen
import ucf.visor.ui.theme.VisorTheme
import ucf.visor.ui.viewmodel.VisorViewModel
import ucf.visor.ui.voice.VoiceCommand
import ucf.visor.ui.voice.VoiceListeningGlow
import ucf.visor.ui.voice.VoiceNavState
import ucf.visor.ui.voice.VoiceNavigationController
import ucf.visor.ui.voice.VoiceReplies


// VisorLayout() will control the application screen state. It calls the separate screen functions
// based on the current viewModel state (uiState) and is where we keep our debugging tools.
//
// talk says something at its kind's volume (see AudioMix); speakAt, at a given
// volume, for Settings previews; onCapture takes a picture and answers it for
// one camera feature (MainActivity). reading pauses, resumes and stops a long
// answer being read aloud (tts/Readings), from the bar along the bottom of
// every screen, the glasses, or "VISOR GO, stop".
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisorLayout(
    viewModel: VisorViewModel,
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
    talk: (String, VoiceCategory) -> Unit = { _, _ -> },
    speakAt: (String, Float) -> Unit = { _, _ -> },
    playSample: (SoundCategory) -> Unit = {},
    lastSpoken: () -> Speaker.Utterance? = { null },
    onCapture: (CameraCommand) -> Unit = {},
    voiceNav: VoiceNavigationController? = null,
    glassesNav: GlassesNavigationController? = null,
    reading: ReadingControls? = null,
    modifier: Modifier = Modifier,
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val voiceState by (voiceNav?.state ?: remember { MutableStateFlow(VoiceNavState.IDLE) })
        .collectAsStateWithLifecycle()
    val readingState by (reading?.reading ?: remember { MutableStateFlow(ReadingState.NONE) })
        .collectAsStateWithLifecycle()
    val readingText by (reading?.readingText ?: remember { MutableStateFlow<String?>(null) })
        .collectAsStateWithLifecycle()
    val lastAnswer by viewModel.lastAnswer.collectAsStateWithLifecycle()

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
    // Voice navigation listening, felt as well as seen (see VoiceListeningGlow
    // below), for a user who isn't looking at the screen: a firm buzz as the
    // microphone opens for a command, and a light tick as it closes. The
    // microphone opens a moment after VISOR says "Listening." (see
    // VoiceNavState.PROMPTING), and the buzz waits for it, so it means "speak now".
    val haptics = LocalHaptics.current
    LaunchedEffect(voiceNav, haptics) {
        voiceNav?.state
            ?.map { it == VoiceNavState.LISTENING }
            ?.distinctUntilChanged()
            ?.drop(1) // the state at launch is not a change
            ?.collect { listening ->
                if (listening) haptics?.listeningStarted() else haptics?.listeningStopped()
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
    LaunchedEffect(uiState.isVerifyingAccount) {
        if (uiState.isVerifyingAccount) {
            navController.navigate("verify_account") { launchSingleTop = true }
            viewModel.onNavigationHandled()
        }
    }

    // Observe ResetPasswordScreen
    LaunchedEffect(uiState.isResettingPassword) {
        if (uiState.isResettingPassword) {
            navController.navigate("reset_password") { launchSingleTop = true }
            viewModel.onNavigationHandled()
        }
    }

    // Observe HomeScreen
    LaunchedEffect(uiState.goingHome) {
        if (uiState.goingHome) {
            navController.navigate("home") {
                launchSingleTop = true
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                restoreState = true
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

    // Observe Phase1ModeConfigurationScreen (Debug menu → Test Suite). Opened
    // like the head-motion lab: one more screen in VISOR's own navigation, with
    // the same navigation bar, so Home's Last answer card is a tap away.
    LaunchedEffect(uiState.atPhase1) {
        if (uiState.atPhase1) {
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
    LaunchedEffect(glassesNav, currentRoute, uiState, readingState) {
        glassesNav?.showScreen(glassesNavScreenFor(currentRoute, uiState, readingState))
    }

    ///////////////////////////////////////////////////////////////////////////
    // Voice + Glasses-Tap Navigation Observer
    LaunchedEffect(voiceNav, glassesNav) {
        // What VISOR says back about a command itself, and, once it has done
        // what was asked, where it went or what it changed (VoiceReplies): a
        // user who isn't looking at the screen would otherwise hear nothing.
        val reply: (String) -> Unit = { talk(it, VoiceCategory.VOICE_COMMANDS) }
        // Where a command went. A screen reader already says each screen's
        // name as it opens (VisorNavHost), so VISOR doesn't say it as well.
        val announce: (String) -> Unit = { if (!context.isScreenReaderOn()) reply(it) }

        merge(
            voiceNav?.commands ?: emptyFlow(),
            glassesNav?.commands ?: emptyFlow()
        ).collect { command ->
            when (command) {
                // Home/Settings/Help/Pairing/camera are all post-login-only —
                // none of them have a touch equivalent before uiState.isAuthComplete
                // either (no bottom nav bar yet), so voice shouldn't uniquely
                // unlock them from TitleScreen/Login/SignUp.
                VoiceCommand.GoHome ->
                    if (uiState.isAuthComplete) {
                        viewModel.home()
                        announce(VoiceReplies.HOME)
                    } else {
                        reply("Log in first to go home.")
                    }

                VoiceCommand.OpenSettings ->
                    if (uiState.isAuthComplete) {
                        viewModel.settings()
                        announce(VoiceReplies.SETTINGS)
                    } else {
                        reply("Log in first to open settings.")
                    }

                is VoiceCommand.OpenSettingsSection ->
                    if (uiState.isAuthComplete) {
                        viewModel.settings(command.section)
                        announce(VoiceReplies.settingsSection(command.section))
                    } else {
                        reply("Log in first to open settings.")
                    }

                VoiceCommand.OpenHelp ->
                    if (uiState.isAuthComplete) {
                        viewModel.help()
                        announce(VoiceReplies.HELP)
                    } else {
                        reply("Log in first for help.")
                    }

                VoiceCommand.PairDevice ->
                    if (uiState.isAuthComplete) {
                        viewModel.hardwarePairing()
                        announce(VoiceReplies.PAIR)
                    } else {
                        reply("Log in first to pair your glasses.")
                    }

                VoiceCommand.GoBack ->
                    if (navController.popBackStack()) announce(VoiceReplies.BACK)
                    else reply("There's nowhere to go back to.")

                // From the glasses: one of Home's camera buttons, from wherever
                // the phone is. The answer is said, and kept on Home's Last answer card.
                is VoiceCommand.RunCamera ->
                    if (uiState.isAuthComplete) onCapture(command.command)
                    else reply("Log in first to take a picture.")

                // Before login only, as their buttons are. viewModel.login()
                // also ends the saved login, so once logged in "log in" (or a
                // misheard "logging") would log the user out with no "Are you
                // sure?". Logging out is "log out", which asks first.
                VoiceCommand.GoToLogin ->
                    if (uiState.isAuthComplete) {
                        reply(VoiceReplies.ALREADY_LOGGED_IN)
                    } else {
                        viewModel.login()
                        announce(VoiceReplies.LOG_IN)
                    }

                VoiceCommand.GoToSignUp ->
                    if (uiState.isAuthComplete) {
                        reply(VoiceReplies.ALREADY_LOGGED_IN)
                    } else {
                        viewModel.signUp()
                        announce(VoiceReplies.SIGN_UP)
                    }

                VoiceCommand.GoToForgotPassword ->
                    if (uiState.isAuthComplete) {
                        reply(VoiceReplies.ALREADY_LOGGED_IN)
                    } else {
                        viewModel.forgotPassword()
                        announce(VoiceReplies.FORGOT_PASSWORD)
                    }

                VoiceCommand.LogOut ->
                    if (uiState.isAuthComplete) viewModel.requestLogoutConfirm()
                    else reply("You're not logged in.")

                // Offline, as Settings' button is: no "Are you sure?" for
                // something that can't happen.
                VoiceCommand.DeleteAccount -> when {
                    !uiState.isAuthComplete -> reply("You're not logged in.")
                    !uiState.isOnline -> reply(context.getString(R.string.settings_delete_account_offline))
                    else -> viewModel.requestDeleteAccountConfirm()
                }

                VoiceCommand.ToggleHighContrast -> {
                    val on = !profile.appHighContrast
                    viewModel.updateProfile(profile.copy(appHighContrast = on))
                    reply(VoiceReplies.highContrast(on))
                }

                // As the Pair screen's choice is, so post-login like it; said
                // back, with how to hold the phone when that is the camera.
                is VoiceCommand.UseCamera ->
                    if (uiState.isAuthComplete) {
                        viewModel.updateProfile(profile.copy(camera = command.camera))
                        reply(
                            when (command.camera) {
                                CameraChoice.PHONE -> SpokenPhrases.USING_PHONE_CAMERA
                                CameraChoice.GLASSES -> SpokenPhrases.USING_GLASSES_CAMERA
                            }
                        )
                    } else {
                        reply("Log in first to choose a camera.")
                    }

                is VoiceCommand.SetSpeechRate -> {
                    viewModel.updateProfile(profile.copy(speechRate = command.rate))
                    reply(VoiceReplies.speechRate(command.rate))
                }

                VoiceCommand.SpeakFaster -> {
                    val rate = SpeechRates.faster(profile.speechRate)
                    viewModel.updateProfile(profile.copy(speechRate = rate))
                    reply("Faster. ${SpeechRates.spokenLabel(rate)}.")
                }

                VoiceCommand.SpeakSlower -> {
                    val rate = SpeechRates.slower(profile.speechRate)
                    viewModel.updateProfile(profile.copy(speechRate = rate))
                    reply("Slower. ${SpeechRates.spokenLabel(rate)}.")
                }

                is VoiceCommand.SetVerbosity -> {
                    viewModel.updateProfile(profile.copy(verbosity = command.verbosity))
                    reply(VoiceReplies.detail(command.verbosity))
                }

                is VoiceCommand.SetTextScale -> {
                    viewModel.updateProfile(profile.copy(textScale = command.scale))
                    reply(VoiceReplies.textSize(command.scale))
                }

                // logout()/confirmDeleteAccount() already clear their own
                // confirm-dialog flag as part of the bigger session reset —
                // no separate cancelXConfirm() call needed here.
                VoiceCommand.Confirm -> when {
                    uiState.isLogoutConfirmVisible -> viewModel.logout()
                    uiState.isDeleteAccountConfirmVisible -> viewModel.confirmDeleteAccount()
                    else -> reply(VoiceReplies.NOTHING_TO_CONFIRM)
                }

                VoiceCommand.Cancel -> when {
                    uiState.isLogoutConfirmVisible -> {
                        viewModel.cancelLogoutConfirm()
                        reply(VoiceReplies.CANCELLED)
                    }

                    uiState.isDeleteAccountConfirmVisible -> {
                        viewModel.cancelDeleteAccountConfirm()
                        reply(VoiceReplies.CANCELLED)
                    }

                    else -> reply(VoiceReplies.NOTHING_TO_CANCEL)
                }

                // "Next" and "skip" are not commands here: they only mean
                // something inside the onboarding wizard's own voice-first flow,
                // which listens directly (no "VISOR GO" needed) and doesn't go
                // through this dispatcher — see ProfileCreationScreen.
                // Said again as the kind of speech it was, at that kind's volume.
                VoiceCommand.RepeatLast -> lastSpoken()?.let { talk(it.text, it.category) }
                    ?: reply("I haven't said anything yet.")

                // "VISOR GO" itself paused the reading ("Listening." was said
                // over it), so by now a reading being read is a paused one.
                // Resuming says nothing first: the reading carrying on is the reply.
                VoiceCommand.StopReading ->
                    if (reading != null && reading.reading.value != ReadingState.NONE) {
                        reading.stop()
                        reply(VoiceReplies.READING_STOPPED)
                    } else {
                        reply(VoiceReplies.NOTHING_BEING_READ)
                    }

                VoiceCommand.PauseReading ->
                    if (reading != null && reading.reading.value != ReadingState.NONE) {
                        reading.pause()
                        reply(VoiceReplies.READING_PAUSED)
                    } else {
                        reply(VoiceReplies.NOTHING_BEING_READ)
                    }

                VoiceCommand.ResumeReading ->
                    if (reading != null && reading.reading.value == ReadingState.PAUSED) reading.resume()
                    else reply(VoiceReplies.NOTHING_TO_RESUME)

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
                // At the top of every screen: anything that went wrong
                // (uiState.recentError), until it is dismissed.
                topBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .windowInsetsPadding(WindowInsets.statusBars),
                    ) {
                        // Above any error: while offline, it says why a
                        // feature is locked before anything is pressed.
                        ConnectivityNotice(
                            isOnline = uiState.isOnline,
                            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
                            talk = talk,
                        )
                        ErrorBanner(
                            message = uiState.recentError,
                            onDismiss = { viewModel.clearRecentError() },
                            // Not said again if VISOR has just said it, as it
                            // does when a camera command can't run.
                            talk = { text, category ->
                                if (lastSpoken()?.text != uiState.recentError) talk(text, category)
                            },
                        )
                    }
                },
                // The navigation bar goes in the Scaffold's bottom bar slot, so the
                // Scaffold pads the screen above it by exactly the bar's height.
                //
                // It used to sit in a Column under the screen, with the Scaffold's
                // padding (which ends with the system navigation bar's height) on
                // the screen. NavigationBar also pads itself for the system bar, so
                // that height was added twice: the second time as a blank band
                // between each screen and the navigation bar.
                bottomBar = {
                    Column {
                        // While VISOR reads a long answer aloud: Pause (or
                        // Resume) and Stop, above the tabs. Not on Home while
                        // it reads the last answer: the Last answer card has
                        // them, under the answer itself. The navigation bar
                        // keeps clear of the system's own; without it, the
                        // controls do.
                        val inLastAnswerCard = currentRoute == "home" &&
                            readingText != null && readingText == lastAnswer?.text
                        ReadingControlsBar(
                            state = if (inLastAnswerCard) ReadingState.NONE else readingState,
                            onPause = { reading?.pause() },
                            onResume = { reading?.resume() },
                            onStop = { reading?.stop() },
                            modifier = if (uiState.navigationBarEnabled) Modifier else Modifier.navigationBarsPadding(),
                        )
                        // Once the user is fully logged in...
                        if (uiState.navigationBarEnabled) {
                            VisorNavigationBar(currentRoute, viewModel)
                        }
                    }
                },
                floatingActionButton = {
                    // DEBUG TOOLS: includes MockDeviceKit and debug screen viewer.
                    // The debug menu can hide this button until VISOR restarts.
                    // Small, so it covers less of the screen in demos and tester
                    // sessions.
                    if (BuildConfig.DEBUG && !uiState.isDebugButtonHidden) {
                        SmallFloatingActionButton(
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
                            speakAt = speakAt,
                            playSample = playSample,
                            voiceNav = voiceNav,
                            onRequestWearablesPermission = onRequestWearablesPermission,
                            onCapture = onCapture,
                            reading = reading,
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