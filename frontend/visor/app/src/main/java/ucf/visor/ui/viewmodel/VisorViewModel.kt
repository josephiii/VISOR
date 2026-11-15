package ucf.visor.ui.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.DeviceSelector
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ucf.visor.VisorApp
import ucf.visor.data.UserProfile
import ucf.visor.engine.VisorState
import ucf.visor.network.ApiResult
import ucf.visor.network.AuthApi
import ucf.visor.stt.CameraCommand

/**
 * What the screens show, and their navigation requests. What outlives the
 * screens (the profile, the glasses, the camera features' answers) is kept in
 * the process-wide [VisorState], which VISOR's engine also answers from with
 * the screen off; this ViewModel shows it and passes changes on to it.
 */
class VisorViewModel(application: Application) : AndroidViewModel(application) {
    ////////////////////////////////////////////////////////////////////////////////////////////////
    // VISOR
    private val state: VisorState = VisorApp.from(application).state
    private val sessionStore = state.sessionStore
    private val profileStore = state.profileStore
    val startDestination: String = if (sessionStore.isLoggedIn()) "home" else "title"

    private val _uiState = MutableStateFlow(
        VisorUiState(
            isAuthComplete = sessionStore.isLoggedIn(),
            isOnline = state.isOnline.value,
        )
    )
    val uiState: StateFlow<VisorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            state.isOnline.collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
        viewModelScope.launch { state.errors.collect(::setRecentError) }
        viewModelScope.launch {
            state.registrationState.collect { value -> _uiState.update { it.copy(registrationState = value) } }
        }
        viewModelScope.launch {
            state.isDatAppUpdateRequired.collect { required ->
                _uiState.update { it.copy(isDatAppUpdateRequired = required) }
            }
        }
        viewModelScope.launch {
            state.isFirmwareUpdateRequired.collect { required ->
                _uiState.update { it.copy(isFirmwareUpdateRequired = required) }
            }
        }
        viewModelScope.launch {
            state.devices.collect { value -> _uiState.update { it.copy(devices = value.toImmutableList()) } }
        }
        viewModelScope.launch {
            state.glassesStatus.collect { status -> _uiState.update { it.copy(glassesStatus = status) } }
        }
    }

    val userProfile: StateFlow<UserProfile> = state.profile

    /**
     * What each camera feature is doing (see [onCaptureStarted]), for its
     * button on Home. A feature that isn't working isn't in it: it is idle.
     */
    val captures = state.captures

    /** What a camera feature last answered, for Home's Last answer card. */
    val lastAnswer = state.lastAnswer

    /** Today's answers, newest first, for Settings > History. */
    val history = state.history

    /** Settings > History's "Clear history", once confirmed. */
    fun clearHistory() = state.clearHistory()

    /** Which microphone VISOR hears commands through right now (Settings > Commands). */
    val listeningThrough = state.listeningThrough

    /** Whether VISOR is listening with the screen off right now (background listening). */
    val backgroundListeningActive = state.backgroundListeningActive

    /** What VISOR is doing about a lost link to the glasses, for the notice at the top of every screen. */
    val glassesConnection = state.glassesConnection

    fun updateProfile(profile: UserProfile) = state.updateProfile(profile)

    /** [command] has been asked for: VISOR is taking its picture. Call on the main thread. */
    fun onCaptureStarted(command: CameraCommand) = state.onCaptureStarted(command)

    /** The glasses have taken [command]'s picture: VISOR is working out what is in it. */
    fun onPictureTaken(command: CameraCommand) = state.onPictureTaken(command)

    /** [command] has answered with [text], which VISOR is now saying. */
    fun onAnswered(command: CameraCommand, text: String) = state.onAnswered(command, text)

    /** Shows [text] on Home's Last answer card, as an answer [command] has just given. */
    fun showAnswer(text: String, command: CameraCommand) = state.showAnswer(text, command)

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // MWDAT
    // AutoDeviceSelector automatically selects the first available wearable device.
    val deviceSelector: DeviceSelector get() = state.deviceSelector

    fun startRegistration(activity: Activity) {
        Wearables.startRegistration(activity)
    }

    fun startUnregistration(activity: Activity) {
        Wearables.startUnregistration(activity)
    }

    fun openFirmwareUpdate(activity: Activity) {
        Wearables.openFirmwareUpdate(activity).onFailure { error, _ ->
            setRecentError(error.description)
        }
    }

    fun openDATGlassesAppUpdate(activity: Activity) {
        Wearables.openDATGlassesAppUpdate(activity).onFailure { error, _ ->
            setRecentError(error.description)
        }
    }

    // UI Updates
    ////////////////////////////////////////////////////////////////////////////////////////////////
    // VISOR
    internal fun setRecentError(error: String) {
        _uiState.update { it.copy(recentError = error) }
    }

    fun clearRecentError() {
        _uiState.update { it.copy(recentError = null) }
    }

    fun showDebugMenu() {
        _uiState.update { it.copy(isDebugMenuVisible = true) }
    }

    fun hideDebugMenu() {
        _uiState.update { it.copy(isDebugMenuVisible = false) }
    }

    /**
     * Hides the debug menu's floating button, e.g. for a demo. Not saved
     * anywhere, so it is back the next time VISOR starts (a rotation keeps it
     * hidden: this ViewModel outlives that).
     */
    fun hideDebugButton() {
        _uiState.update { it.copy(isDebugButtonHidden = true) }
    }

    /**
     * Clears every pending navigation request.
     *
     * The screen flags in [VisorUiState] are one-shot *events*, not state:
     * VisorLayout watches each with a `LaunchedEffect` keyed on the flag, and a
     * `LaunchedEffect` only re-runs when its key changes. So a flag left true
     * after its navigation has happened silently disables that destination —
     * asking for it again re-sets a flag that is already true, the key never
     * changes, and nothing moves.
     *
     * That is what made the navigation bar go dead after a system back press:
     * back pops the NavController without telling this ViewModel, leaving the
     * flag for the screen the user just left still true, so its bar item did
     * nothing. Consuming the flag the moment VisorLayout has acted on it keeps
     * every request a genuine false -> true transition.
     *
     * Only navigation intents are cleared here. Session state such as
     * `isAuthComplete` is real state and must survive.
     */
    fun onNavigationHandled() {
        _uiState.update {
            it.copy(
                atTitle = false,
                isLoggingIn = false,
                isSigningUp = false,
                hasForgottenPassword = false,
                isEnteringCode = false,
                isVerifyingAccount = false,
                isResettingPassword = false,
                goingHome = false,
                isPairingHardware = false,
                atSettings = false,
                atHelp = false,
                atMotionLab = false,
                isOnboarding = false,
                atPhase1 = false,
            )
        }
    }

    fun login() {
        _uiState.update { it.copy(isSigningUp = false) }
        _uiState.update { it.copy(isLoggingIn = true) }
        _uiState.update { it.copy(isAuthComplete = false) }
        sessionStore.setLoggedIn(false)
        state.onSessionChanged()
    }

    /**
     * Called by LoginScreen after POST /auth/login succeeds. Persists the
     * session tokens, marks the device logged in, and advances to Home.
     * This is the single place the session is established — register alone
     * does NOT log a user in (the backend returns only a user_id).
     *
     * The profile (speech rate, contrast, text size...) lives only on the
     * phone, so a login with no saved profile (a new phone), or one whose
     * sign-up here never finished setup, goes through the voice-first setup
     * first, which ends in home() itself (see ProfileStore.needsSetup).
     * A brand-new account comes through [onSignUpSuccess] instead.
     */
    fun onLoginSuccess(accessToken: String, refreshToken: String) {
        sessionStore.saveTokens(accessToken, refreshToken)
        if (profileStore.needsSetup()) {
            onboard()
        } else {
            home() // sets isAuthComplete + sessionStore.setLoggedIn(true), navigates home
        }
    }

    /**
     * Called by SignUpScreen once the new account is registered and logged in.
     * A new account has never been set up, so it always goes through setup,
     * whatever this phone has saved: going by whether a profile was saved
     * here sent a new account straight to Home, with only the navigation bar
     * to show for it, on any phone that had been set up before (by a tester,
     * or the last user, as logging out keeps the profile) or where a setting
     * had been changed by voice before signing up.
     */
    fun onSignUpSuccess(accessToken: String, refreshToken: String) {
        sessionStore.saveTokens(accessToken, refreshToken)
        state.startNewAccount()
        onboard()
    }

    /**
     * Setup's Start using VISOR (the button, or "start" said aloud): keeps
     * the answers, then goes Home as a logged-in user.
     */
    fun finishSetup(profile: UserProfile) {
        state.finishSetup(profile)
        home()
    }

    /** Explicit "log out" from Settings — returns all the way to TitleScreen
     *  (not just the login form) and clears the whole back stack, so nothing
     *  from the ended session stays reachable by pressing back afterward. */
    fun logout() {
        _uiState.update {
            it.copy(
                isLoggingIn = false,
                isSigningUp = false,
                goingHome = false,
                atSettings = false,
                atHelp = false,
                isPairingHardware = false,
                isAuthComplete = false,
                isLogoutConfirmVisible = false,
                isDeleteAccountConfirmVisible = false,
                atTitle = true,
            )
        }
        sessionStore.clear() // drops tokens + loggedIn flag
        state.onSessionChanged()
        // It may be text read off someone's mail: not for the next person to log in.
        state.clearLastAnswer()
        state.clearHistory()
    }

    /**
     * Deletes the account on the backend (DELETE /auth/deleteAccount), then
     * wipes the saved profile and ends the session, landing back on
     * TitleScreen same as logout(). If the backend refuses, nothing local is
     * touched and the user is told why, so no one is told an account is gone
     * when it is not.
     */
    fun confirmDeleteAccount() {
        _uiState.update { it.copy(isDeleteAccountConfirmVisible = false) }
        // Settings locks the button offline; this is for "delete my account" said aloud.
        if (!_uiState.value.isOnline) {
            setRecentError(DELETE_ACCOUNT_OFFLINE)
            return
        }
        val token = sessionStore.accessToken()
        if (token.isNullOrBlank()) {
            setRecentError(DELETE_ACCOUNT_LOG_IN_AGAIN)
            return
        }
        viewModelScope.launch {
            when (val result = AuthApi.deleteAccount(token)) {
                is ApiResult.Success -> {
                    state.resetProfile()
                    logout()
                }

                // 401: access tokens expire, and VISOR does not refresh them yet.
                is ApiResult.Error -> setRecentError(
                    if (result.code == 401) DELETE_ACCOUNT_LOG_IN_AGAIN
                    else "Couldn't delete your account: ${result.message}"
                )
            }
        }
    }

    fun signUp() {
        _uiState.update { it.copy(isLoggingIn = false) }
        _uiState.update { it.copy(isSigningUp = true) }
    }

    fun forgotPassword() {
        _uiState.update { it.copy(isLoggingIn = false) }
        _uiState.update { it.copy(hasForgottenPassword = true) }
    }

    fun enterCode() {
        _uiState.update { it.copy(hasForgottenPassword = false) }
        _uiState.update { it.copy(isVerifyingAccount = false) }
        _uiState.update { it.copy(isEnteringCode = true) }
    }

    /** Sign-up (or a login that found the email unverified) lands here to enter the emailed code. */
    fun verifyAccount(email: String) {
        _uiState.update { it.copy(pendingEmail = email) }
        _uiState.update { it.copy(isLoggingIn = false) }
        _uiState.update { it.copy(isSigningUp = false) }
        _uiState.update { it.copy(isVerifyingAccount = true) }
    }

    /**
     * Called by EnterCodeScreen after POST /auth/verify succeeds. The backend
     * returns a session, so save it and send the new user through onboarding.
     */
    fun onEmailVerified(accessToken: String, refreshToken: String) {
        _uiState.update { it.copy(pendingEmail = "") }
        // A verified sign-up is a new account like any other.
        onSignUpSuccess(accessToken, refreshToken)
    }

    fun resetPassword() {
        _uiState.update { it.copy(hasForgottenPassword = false) }
        _uiState.update { it.copy(isResettingPassword = true) }
    }

    fun home() {
        _uiState.update { it.copy(isPairingHardware = false) }
        _uiState.update { it.copy(atSettings = false) }
        _uiState.update { it.copy(goingHome = true) }
        _uiState.update { it.copy(isAuthComplete = true) }
        sessionStore.setLoggedIn(true)
        state.onSessionChanged()
    }

    fun hardwarePairing() {
        _uiState.update { it.copy(goingHome = false) }
        _uiState.update { it.copy(atSettings = false) }
        _uiState.update { it.copy(isPairingHardware = true) }
    }

    /** Opens Settings at [section], or at its hub of sections when null (the navigation bar's tab). */
    fun settings(section: SettingsSection? = null) {
        _uiState.update { it.copy(goingHome = false) }
        _uiState.update { it.copy(isPairingHardware = false) }
        _uiState.update { it.copy(atSettings = true, settingsSection = section) }
    }

    /** Within Settings: opens [section], or goes back to the hub when null. */
    fun openSettingsSection(section: SettingsSection?) {
        _uiState.update { it.copy(settingsSection = section) }
    }

    fun help() {
        _uiState.update { it.copy(atHelp = true) }
    }

    fun closeHelp() {
        _uiState.update { it.copy(atHelp = false) }
    }

    /** The head-motion lab (Debug menu → Test Suite): MWDAT 1.0 Motion + camera trials. */
    fun motionLab() {
        _uiState.update { it.copy(atMotionLab = true) }
    }

    /**
     * Phase 1's demo scripts (Debug menu → Test Suite): each stands in for a
     * camera feature's answer to a photo. Opened as one more screen in VISOR's
     * own navigation, so what a tester sees around it is what a user sees.
     */
    fun phase1() {
        _uiState.update { it.copy(atPhase1 = true) }
    }

    fun requestLogoutConfirm() {
        _uiState.update { it.copy(isLogoutConfirmVisible = true) }
    }

    fun cancelLogoutConfirm() {
        _uiState.update { it.copy(isLogoutConfirmVisible = false) }
    }

    fun requestDeleteAccountConfirm() {
        _uiState.update { it.copy(isDeleteAccountConfirmVisible = true) }
    }

    fun cancelDeleteAccountConfirm() {
        _uiState.update { it.copy(isDeleteAccountConfirmVisible = false) }
    }

    fun onboard() {
        _uiState.update { it.copy(isEnteringCode = false) }
        _uiState.update { it.copy(isSigningUp = false) }
        _uiState.update { it.copy(isOnboarding = true) }
    }

    /** What VISOR did for one "Hey Meta, start VISOR", and what to tell the wearer. */
    data class VoiceLaunchResult(
        val success: Boolean,
        val spoken: String,
    )

    /**
     * The action behind a voice launch: go Home, ready for the first question.
     * Every camera feature works as soon as the wearer is logged in, so there
     * is nothing else to start. Safe to repeat, because a launch can reach
     * VISOR twice (the launch intent and the voice-invocation stream).
     */
    fun openFromVoice(): VoiceLaunchResult {
        if (!_uiState.value.isAuthComplete) {
            return VoiceLaunchResult(
                success = false,
                spoken = "VISOR is open. Please log in on your phone to start.",
            )
        }
        home()
        return VoiceLaunchResult(success = true, spoken = "VISOR is ready.")
    }

    fun toggleNavigationBar() {
        _uiState.update {
            if (it.isAuthComplete)
                it.copy(isAuthComplete = false)
            else
                it.copy(isAuthComplete = true)
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // MWDAT
    fun onPermissionsResult(permissionsResult: Map<String, Boolean>, onAllGranted: () -> Unit) {
        val granted = permissionsResult.entries.all { it.value }
        _uiState.update { it.copy(canRegister = granted) }
        if (granted) {
            onAllGranted()
            state.startMonitoring()
        } else {
            _uiState.update {
                it.copy(recentError = "Allow All Permissions (Bluetooth, Bluetooth Connect, Internet)")
            }
        }
    }

    private companion object {
        const val DELETE_ACCOUNT_LOG_IN_AGAIN =
            "Couldn't delete your account. Please log out, log back in, and try again."
        const val DELETE_ACCOUNT_OFFLINE =
            "Deleting your account needs the internet, and you're offline. Your account hasn't changed."
    }
}
