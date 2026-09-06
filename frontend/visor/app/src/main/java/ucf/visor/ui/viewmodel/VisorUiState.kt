// WearablesUiState - DAT API State Management
//
// This data class aggregates DAT API state for the UI layer

package ucf.visor.ui.viewmodel

import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class VisorUiState(
    // Post-MWDAT
    val registrationState: RegistrationState = RegistrationState.UNAVAILABLE,
    val devices: ImmutableList<DeviceIdentifier> = persistentListOf(),

    val isStreaming: Boolean = false,
    val isDebugMenuVisible: Boolean = false,
    val isGettingStartedSheetVisible: Boolean = false,
    val isFirmwareUpdateRequired: Boolean = false,
    val isDatAppUpdateRequired: Boolean = false,
    val hasActiveDevice: Boolean = false,
    val canRegister: Boolean = false,

    // Interface Error Component.
    val recentError: String? = null,

    // Screen States.
    val isLoggingIn: Boolean = false,
    val isSigningUp: Boolean = false,
    val hasForgottenPassword: Boolean = false,
    val isEnteringCode: Boolean = false,
    val isVerifyingAccount: Boolean = false,
    val isResettingPassword: Boolean = false,
    val goingHome: Boolean = false,
    val isPairingHardware: Boolean = false,
    val atSettings: Boolean = false,
    val isOnboarding: Boolean = false,

    // Component States.
    val isAuthComplete: Boolean = false,
    val isSessionActive: Boolean = false,

    // PHASE 1 States.
    val phase1Initiated: Boolean = false,
    val isConfiguring: Boolean = false,
    val inHazardAwarenessMode: Boolean = true, // Default.
    val inSceneDescriptionMode: Boolean = false,
    val inReadingAssistanceMode: Boolean = false,
) {
    val isRegistered: Boolean =
        registrationState == RegistrationState.REGISTERED ||
                registrationState == RegistrationState.UNREGISTERING

    val isRegistering: Boolean = registrationState == RegistrationState.REGISTERING

    val canStartRegistration: Boolean = canRegister && !isRegistering

    val finishedOnboarding: Boolean = goingHome

    val navigationBarEnabled: Boolean = isAuthComplete
}
