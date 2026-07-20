package ucf.visor.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    profile: UserProfile,
    onProfileChange: (UserProfile) -> Unit,
    speak: (String) -> Unit = {},
    onLogout: () -> Unit = {},          // TODO: revoke access + refresh tokens (frontend-only per Joseph)
    onDeleteAccount: () -> Unit = {},   // TODO: POST /auth/deleteAccount, then ProfileStore.clear()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Settings",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )

        SettingSection("How fast VISOR talks")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpeechRate.entries.forEach { r ->
                ChoiceChip(
                    label = when (r) { SpeechRate.SLOW -> "Slower"; SpeechRate.NORMAL -> "Normal"; SpeechRate.FAST -> "Faster" },
                    selected = profile.speechRate == r,
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
                ChoiceChip(
                    label = v.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected = profile.verbosity == v,
                    modifier = Modifier.weight(1f),
                ) { onProfileChange(profile.copy(verbosity = v)) }
            }
        }

        SettingSection("Display")
        ChoiceChip(
            label = if (profile.appHighContrast) "High contrast: ON" else "High contrast: OFF",
            selected = profile.appHighContrast,
            modifier = Modifier.fillMaxWidth(),
        ) { onProfileChange(profile.copy(appHighContrast = !profile.appHighContrast)) }

        Spacer(Modifier.height(24.dp))
        SettingSection("Account")
        ChoiceChip("Log out", selected = false, modifier = Modifier.fillMaxWidth(), onClick = onLogout)
        ChoiceChip("Delete my account", selected = false, modifier = Modifier.fillMaxWidth(), onClick = onDeleteAccount)
    }
}

@Composable
private fun SettingSection(title: String) {
    Text(
        title,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(top = 10.dp)
            .semantics { heading() },
    )
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
        ),
    ) {
        Text(
            label,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun SettingsPreview() {
    var profile by remember { mutableStateOf(UserProfile()) }
    SettingsScreen(profile = profile, onProfileChange = { profile = it })
}
