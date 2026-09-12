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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ucf.visor.ui.components.SelectableChip
import ucf.visor.ui.profile.SpeechRate
import ucf.visor.ui.profile.TextScale
import ucf.visor.ui.profile.UserProfile
import ucf.visor.ui.profile.Verbosity
import ucf.visor.ui.viewmodel.VisorViewModel

/**
 * Settings — reads and writes the same UserProfile the creation wizard fills.
 * (Ticket TBD — the "Profile Settings" page from Aidan's VISOR-155 page list.)
 *
 * Stateless: parent owns the profile (load via ProfileStore, save on change):
 *
 *   val store = remember { ProfileStore(context) }
 *   var profile by remember { mutableStateOf(store.load()) }
 *   SettingsScreen(profile = profile, onProfileChange = { profile = it; store.save(it) })
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
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )

        SettingSection("How fast VISOR talks")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpeechRate.entries.forEach { r ->
                SelectableChip(
                    label = when (r) { SpeechRate.SLOW -> "Slower"; SpeechRate.NORMAL -> "Normal"; SpeechRate.FAST -> "Faster" },
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
        SettingSection("Account")
        SelectableChip(
            "Log out",
            selected = false,
            showCheckmark = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = onLogout,
        )
        SelectableChip(
            "Delete my account",
            selected = false,
            showCheckmark = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = onDeleteAccount,
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
