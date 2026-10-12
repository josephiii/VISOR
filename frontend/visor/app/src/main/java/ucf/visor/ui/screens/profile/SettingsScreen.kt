package ucf.visor.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ucf.visor.R
import ucf.visor.ui.components.SelectableChip
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.data.Severity
import ucf.visor.data.SpeechRates
import ucf.visor.data.TextScale
import ucf.visor.data.UserProfile
import ucf.visor.data.Verbosity
import ucf.visor.data.VisionType
import ucf.visor.data.label
import ucf.visor.ui.theme.VisorShapes
import ucf.visor.ui.viewmodel.VisorViewModel

/**
 * Settings — reads and writes the same UserProfile the creation wizard fills.
 */
@Composable
fun SettingsScreen(
    viewModel: VisorViewModel,
    onProfileChange: (UserProfile) -> Unit = {},
    onLogout: () -> Unit = {},          // TODO: revoke access + refresh tokens (frontend-only per Joseph)
    onDeleteAccount: () -> Unit = {},   // VisorViewModel.confirmDeleteAccount: DELETE /auth/deleteAccount, then wipes the profile
    onHelp: () -> Unit = {},
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val showLogoutConfirm = uiState.isLogoutConfirmVisible
    val showDeleteConfirm = uiState.isDeleteAccountConfirmVisible
    val speechRateDescription = stringResource(
        R.string.settings_speech_rate_description,
        SpeechRates.spokenLabel(profile.speechRate),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )

        SettingSection(stringResource(R.string.settings_section_about_you))
        OutlinedTextField(
            value = profile.displayName,
            onValueChange = { onProfileChange(profile.copy(displayName = it)) },
            label = { Text(stringResource(R.string.settings_name_label)) },
            singleLine = true,
            shape = VisorShapes.Control,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            stringResource(R.string.step_vision_title),
            style = MaterialTheme.typography.bodyLarge
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VisionType.entries.forEach { type ->
                val selected = type in profile.visionTypes
                SelectableChip(
                    label = type.label(),
                    selected = selected,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val base = profile.visionTypes - VisionType.NOT_SURE
                    onProfileChange(
                        profile.copy(
                            visionTypes = (if (selected) base - type else base + type)
                                .ifEmpty { setOf(VisionType.NOT_SURE) }
                        )
                    )
                }
            }
        }

        Text(stringResource(R.string.step_severity_title), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Severity.entries.forEach { s ->
                SelectableChip(
                    label = s.label(),
                    selected = profile.severity == s,
                    showCheckmark = false,
                    modifier = Modifier.weight(1f),
                ) { onProfileChange(profile.copy(severity = s)) }
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = profile.visionDescription,
            onValueChange = { onProfileChange(profile.copy(visionDescription = it)) },
            label = { Text(stringResource(R.string.settings_vision_description_label)) },
            shape = VisorShapes.Control,
            modifier = Modifier.fillMaxWidth(),
        )

        SettingSection(stringResource(R.string.settings_section_speech_rate))
        Text(
            text = SpeechRates.label(profile.speechRate),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Slider(
            value = profile.speechRate,
            onValueChange = { position ->
                val rate = SpeechRates.snap(position)
                if (rate != profile.speechRate) onProfileChange(profile.copy(speechRate = rate))
            },
            valueRange = SpeechRates.Min..SpeechRates.Max,
            steps = SpeechRates.SliderSteps,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = speechRateDescription },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.settings_speech_rate_slower), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.settings_speech_rate_faster), style = MaterialTheme.typography.bodySmall)
        }

        SettingSection(stringResource(R.string.settings_section_detail))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Verbosity.entries.forEach { v ->
                SelectableChip(
                    label = stringResource(
                        when (v) {
                            Verbosity.BRIEF -> R.string.settings_verbosity_brief
                            Verbosity.STANDARD -> R.string.settings_verbosity_standard
                            Verbosity.DETAILED -> R.string.settings_verbosity_detailed
                        }
                    ),
                    selected = profile.verbosity == v,
                    showCheckmark = false,
                    modifier = Modifier.weight(1f),
                ) { onProfileChange(profile.copy(verbosity = v)) }
            }
        }

        SettingSection(stringResource(R.string.settings_section_text_size))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextScale.entries.forEach { scale ->
                SelectableChip(
                    label = stringResource(
                        when (scale) {
                            TextScale.STANDARD -> R.string.settings_text_size_standard
                            TextScale.LARGE -> R.string.settings_text_size_large
                            TextScale.EXTRA_LARGE -> R.string.settings_text_size_extra_large
                        }
                    ),
                    selected = profile.textScale == scale,
                    showCheckmark = false,
                    modifier = Modifier.weight(1f),
                ) { onProfileChange(profile.copy(textScale = scale)) }
            }
        }

        SettingSection(stringResource(R.string.settings_section_display))
        SelectableChip(
            label = stringResource(
                if (profile.appHighContrast) R.string.settings_high_contrast_on
                else R.string.settings_high_contrast_off
            ),
            selected = profile.appHighContrast,
            modifier = Modifier.fillMaxWidth(),
        ) { onProfileChange(profile.copy(appHighContrast = !profile.appHighContrast)) }

        SettingSection(stringResource(R.string.settings_section_voice_navigation))
        Text(
            stringResource(R.string.settings_voice_navigation_description),
            style = MaterialTheme.typography.bodyMedium,
        )
        SelectableChip(
            label = stringResource(
                if (profile.voiceNavigationEnabled) R.string.settings_voice_navigation_on
                else R.string.settings_voice_navigation_off
            ),
            selected = profile.voiceNavigationEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) { onProfileChange(profile.copy(voiceNavigationEnabled = !profile.voiceNavigationEnabled)) }

        SettingSection(stringResource(R.string.settings_section_tap_navigation))
        Text(
            stringResource(R.string.settings_tap_navigation_description),
            style = MaterialTheme.typography.bodyMedium,
        )
        SelectableChip(
            label = stringResource(
                if (profile.glassesTapNavigationEnabled) R.string.settings_tap_navigation_on
                else R.string.settings_tap_navigation_off
            ),
            selected = profile.glassesTapNavigationEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            onProfileChange(profile.copy(glassesTapNavigationEnabled = !profile.glassesTapNavigationEnabled))
        }

        Spacer(Modifier.height(24.dp))
        SettingSection(stringResource(R.string.settings_section_support))
        SelectableChip(
            stringResource(R.string.settings_help),
            selected = false,
            showCheckmark = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = onHelp,
        )

        Spacer(Modifier.height(24.dp))
        SettingSection(stringResource(R.string.settings_section_account))
        SelectableChip(
            stringResource(R.string.settings_log_out),
            selected = false,
            vital = true,
            showCheckmark = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = { viewModel.requestLogoutConfirm() },
        )
        SelectableChip(
            stringResource(R.string.settings_delete_account),
            selected = false,
            vital = true,
            showCheckmark = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = { viewModel.requestDeleteAccountConfirm() },
        )
    }

    // Both actions are hard to undo (logging out mid-session, or permanent
    // deletion) so they get a confirm step rather than firing immediately.
    // This state lives in VisorUiState (not local remember) so a spoken
    // "log out"/"delete my account" can open the same dialog a tap would.
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelLogoutConfirm() },
            title = { Text(stringResource(R.string.settings_log_out_confirm_title)) },
            text = { Text(stringResource(R.string.settings_log_out_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelLogoutConfirm(); onLogout() }) {
                    Text(stringResource(R.string.settings_log_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelLogoutConfirm() }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteAccountConfirm() },
            title = { Text(stringResource(R.string.settings_delete_account_confirm_title)) },
            text = { Text(stringResource(R.string.settings_delete_account_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelDeleteAccountConfirm(); onDeleteAccount() }) {
                    Text(stringResource(R.string.settings_delete_account_confirm_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteAccountConfirm() }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SettingSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(top = 10.dp)
            .semantics { heading() },
    )
}
