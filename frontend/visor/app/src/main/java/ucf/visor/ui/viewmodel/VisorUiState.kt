// VisorUiState - the app's screen state: MWDAT registration and device state,
// plus the one-shot navigation flags VisorLayout observes.

package ucf.visor.ui.viewmodel

import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ucf.visor.wearables.GlassesStatus

data class VisorUiState(
    // Post-MWDAT
    val registrationState: RegistrationState = RegistrationState.UNAVAILABLE,
    val devices: ImmutableList<DeviceIdentifier> = persistentListOf(),
    /** The pair VISOR would use (worn and connected first), with MWDAT 1.0 device state. */
    val glassesStatus: GlassesStatus? = null,

    val isDebugMenuVisible: Boolean = false,
    /** Debug menu → Hide debug button. Kept only in memory: the button is back when VISOR restarts. */
    val isDebugButtonHidden: Boolean = false,
    /** A paired device reports DEVICE_UPDATE_REQUIRED: Pair Glasses offers "Update firmware". */
    val isFirmwareUpdateRequired: Boolean = false,
    /** A glasses session was refused until the DAT app on the glasses is updated. */
    val isDatAppUpdateRequired: Boolean = false,
    val canRegister: Boolean = false,

    // Interface Error Component.
    val recentError: String? = null,

    // Screen States.
    val atTitle: Boolean = false,
    val isLoggingIn: Boolean = false,
    val isSigningUp: Boolean = false,
    val hasForgottenPassword: Boolean = false,
    val isEnteringCode: Boolean = false,
    val isVerifyingAccount: Boolean = false,
    val isResettingPassword: Boolean = false,
    val goingHome: Boolean = false,
    val isPairingHardware: Boolean = false,
    val atSettings: Boolean = false,
    val atHelp: Boolean = false,
    val atMotionLab: Boolean = false,
    val isOnboarding: Boolean = false,

    // Email awaiting verification (set at sign-up, or when login finds it unverified).
    val pendingEmail: String = "",

    // Component States.
    val isAuthComplete: Boolean = false,
    val isSessionActive: Boolean = false,
    val isLogoutConfirmVisible: Boolean = false,
    val isDeleteAccountConfirmVisible: Boolean = false,
    /** Which part of Settings is open, or null for the Settings hub. */
    val settingsSection: SettingsSection? = null,

    // PHASE 1 States.
    val phase1Initiated: Boolean = false,
    val isConfiguring: Boolean = false,
) {
    val isRegistered: Boolean =
        registrationState == RegistrationState.REGISTERED ||
                registrationState == RegistrationState.UNREGISTERING

    val isRegistering: Boolean = registrationState == RegistrationState.REGISTERING

    val canStartRegistration: Boolean = canRegister && !isRegistering

    val navigationBarEnabled: Boolean = isAuthComplete
}
