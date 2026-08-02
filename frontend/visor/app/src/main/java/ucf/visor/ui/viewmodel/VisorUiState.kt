/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// WearablesUiState - DAT API State Management
//
// This data class aggregates DAT API state for the UI layer

package ucf.visor.ui.viewmodel

import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class VisorUiState(
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
    val isOnboarding: Boolean = false,
    val atHome: Boolean = false,
    val onHardwareConnection: Boolean = false,
    val authComplete: Boolean = false,
) {
    val isRegistered: Boolean =
        registrationState == RegistrationState.REGISTERED ||
                registrationState == RegistrationState.UNREGISTERING

    val isRegistering: Boolean = registrationState == RegistrationState.REGISTERING

    val canStartRegistration: Boolean = canRegister && !isRegistering

    val finishedOnboarding: Boolean = atHome
}
