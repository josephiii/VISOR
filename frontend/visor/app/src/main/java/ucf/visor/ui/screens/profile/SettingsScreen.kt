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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ucf.visor.ui.components.SelectableChip
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.ui.profile.Severity
import ucf.visor.ui.profile.SpeechRates
import ucf.visor.ui.profile.TextScale
import ucf.visor.ui.profile.UserProfile
import ucf.visor.ui.profile.Verbosity
import ucf.visor.ui.profile.VisionType
import ucf.visor.ui.profile.label
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
    onDeleteAccount: () -> Unit = {},   // TODO: POST /auth/deleteAccount, then ProfileStore.clear()
    onHelp: () -> Unit = {},
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val showLogoutConfirm = uiState.isLogoutConfirmVisible
    val showDeleteConfirm = uiState.isDeleteAccountConfirmVisible

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )

        SettingSection("About you")
        OutlinedTextField(
            value = profile.displayName,
            onValueChange = { onProfileChange(profile.copy(displayName = it)) },
            label = { Text("Your name") },
            singleLine = true,
            shape = VisorShapes.Control,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            "Which of these describe what you experience?",
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

        Text("How much does it affect daily life?", style = MaterialTheme.typography.bodyLarge)
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
            label = { Text("Describe your vision, in your own words (optional)") },
            shape = VisorShapes.Control,
            modifier = Modifier.fillMaxWidth(),
        )

        SettingSection("How fast VISOR talks")
        Text(
            text = SpeechRates.label(profile.speechRate),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        // Deliberately silent while dragging. A user reaching for this slider
        // has already decided they want faster or slower speech, so speaking
        // every notch talks over the very thing they are trying to tune — and
        // the next thing VISOR says demonstrates the new rate anyway.
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
                .semantics { contentDescription = "Speech rate: ${SpeechRates.spokenLabel(profile.speechRate)}" },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Slower", style = MaterialTheme.typography.bodySmall)
            Text("Faster", style = MaterialTheme.typography.bodySmall)
        }

        SettingSection("How much detail")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Verbosity.entries.forEach { v ->
                SelectableChip(
                    label = v.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected = profile.verbosity == v,
                    showCheckmark = false,
                    modifier = Modifier.weight(1f),
                ) { onProfileChange(profile.copy(verbosity = v)) }
            }
        }

        SettingSection("Text size")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextScale.entries.forEach { scale ->
                SelectableChip(
                    label = when (scale) {
                        TextScale.STANDARD -> "Standard"
                        TextScale.LARGE -> "Large"
                        TextScale.EXTRA_LARGE -> "Extra large"
                    },
                    selected = profile.textScale == scale,
                    showCheckmark = false,
                    modifier = Modifier.weight(1f),
                ) { onProfileChange(profile.copy(textScale = scale)) }
            }
        }

        SettingSection("Display")
        SelectableChip(
            label = if (profile.appHighContrast) "High contrast: ON" else "High contrast: OFF",
            selected = profile.appHighContrast,
            modifier = Modifier.fillMaxWidth(),
        ) { onProfileChange(profile.copy(appHighContrast = !profile.appHighContrast)) }

        SettingSection("Voice navigation")
        Text(
            "Say \"VISOR GO\" any time, then say where you'd like to go — " +
                    "for example \"open settings\" or \"go home\".",
            style = MaterialTheme.typography.bodyMedium,
        )
        SelectableChip(
            label = if (profile.voiceNavigationEnabled) "Voice navigation: ON" else "Voice navigation: OFF",
            selected = profile.voiceNavigationEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) { onProfileChange(profile.copy(voiceNavigationEnabled = !profile.voiceNavigationEnabled)) }

        Spacer(Modifier.height(24.dp))
        SettingSection("Support")
        SelectableChip(
            "Help & how VISOR works",
            selected = false,
            showCheckmark = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = onHelp,
        )

        Spacer(Modifier.height(24.dp))
        SettingSection("Account")
        SelectableChip(
            "Log out",
            selected = false,
            vital = true,
            showCheckmark = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = { viewModel.requestLogoutConfirm() },
        )
        SelectableChip(
            "Delete my account",
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
            title = { Text("Log out?") },
            text = { Text("You'll need to sign in again to use VISOR.") },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelLogoutConfirm(); onLogout() }) {
                    Text("Log out")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelLogoutConfirm() }) { Text("Cancel") }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteAccountConfirm() },
            title = { Text("Delete your account?") },
            text = { Text("This permanently deletes your VISOR account and everything in it. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelDeleteAccountConfirm(); onDeleteAccount() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteAccountConfirm() }) { Text("Cancel") }
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
