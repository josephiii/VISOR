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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ucf.visor.ui.components.SelectableChip
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.ui.profile.Severity
import ucf.visor.ui.profile.SpeechRate
import ucf.visor.ui.profile.TextScale
import ucf.visor.ui.profile.UserProfile
import ucf.visor.ui.profile.Verbosity
import ucf.visor.ui.profile.VisionType
import ucf.visor.ui.profile.label
import ucf.visor.ui.theme.VisorShapes
import ucf.visor.ui.viewmodel.VisorViewModel

/**
 * Settings — reads and writes the same UserProfile the creation wizard fills.
 * (Ticket TBD — the "Profile Settings" page from Aidan's VISOR-155 page list.)
 *
 * Backed by VisorViewModel.userProfile, which loads from and saves to
 * ProfileStore — see VisorViewModel.updateProfile. The "About you" section
 * below is the only place besides onboarding that can change vision type,
 * severity, or the free-text description; previously those were write-once
 * during onboarding with no way to revisit them.
 *
 * Auth actions (logout / delete account / change username) are callbacks —
 * they belong to Joseph's /auth endpoints, not this screen's logic.
 */
@Composable
fun SettingsScreen(
    viewModel: VisorViewModel,
    speak: (String) -> Unit = {},
    onProfileChange: (UserProfile) -> Unit = {},
    onLogout: () -> Unit = {},          // TODO: revoke access + refresh tokens (frontend-only per Joseph)
    onDeleteAccount: () -> Unit = {},   // TODO: POST /auth/deleteAccount, then ProfileStore.clear()
    onHelp: () -> Unit = {},
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

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

        OutlinedTextField(
            value = profile.visionDescription,
            onValueChange = { onProfileChange(profile.copy(visionDescription = it)) },
            label = { Text("Describe your vision, in your own words (optional)") },
            shape = VisorShapes.Control,
            modifier = Modifier.fillMaxWidth(),
        )

        SettingSection("How fast VISOR talks")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpeechRate.entries.forEach { r ->
                SelectableChip(
                    label = when (r) {
                        SpeechRate.SLOW -> "Slower"; SpeechRate.NORMAL -> "Normal"; SpeechRate.FAST -> "Faster"
                    },
                    selected = profile.speechRate == r,
                    showCheckmark = false,
                    modifier = Modifier.weight(1f),
                ) {
                    onProfileChange(profile.copy(speechRate = r))
                    speak("Speech ${if (r == SpeechRate.SLOW) "slower" else if (r == SpeechRate.FAST) "faster" else "normal"}.")
                }
            }
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
            showCheckmark = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = { showLogoutConfirm = true },
        )
        SelectableChip(
            "Delete my account",
            selected = false,
            showCheckmark = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = { showDeleteConfirm = true },
        )
    }

    // Both actions are hard to undo (logging out mid-session, or permanent
    // deletion) so they get a confirm step rather than firing immediately.
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Log out?") },
            text = { Text("You'll need to sign in again to use VISOR.") },
            confirmButton = {
                TextButton(onClick = { showLogoutConfirm = false; onLogout() }) {
                    Text("Log out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete your account?") },
            text = { Text("This permanently deletes your VISOR account and everything in it. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDeleteAccount() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
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
