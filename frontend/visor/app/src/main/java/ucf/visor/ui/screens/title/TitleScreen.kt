package ucf.visor.ui.screens.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ucf.visor.R
import ucf.visor.ui.components.SelectableChip
import ucf.visor.ui.components.VisorButton
import ucf.visor.ui.components.VisorHeader
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.ui.viewmodel.VisorViewModel
import ucf.visor.ui.voice.VoiceNavigationController

/**
 * The very first screen a signed-out user sees — VISOR's front door.
 *
 * Purpose is narrow by design: get to Log In or Sign Up, and (for anyone who
 * wants their hands free before they've even made an account) turn on voice
 * navigation. Nothing here asks for credentials, so unlike the screens behind
 * it, everything on this screen is safe to also drive by voice.
 *
 * A returning, already-logged-in user never sees this screen at all — see
 * VisorViewModel.startDestination.
 */
@Composable
fun TitleScreen(
    viewModel: VisorViewModel,
    talk: (String) -> Unit = {},
    voiceNav: VoiceNavigationController? = null,
    onLoginClick: () -> Unit,
    onSignUpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    var showVoiceTip by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(showVoiceTip) {
        if (showVoiceTip) {
            val tip = "Voice navigation is on. Say VISOR GO, then say what you'd like. " +
                    "Try log in, sign up, go home, or help. " +
                    "You can turn this off any time in Settings."
            voiceNav?.speakGuarded(tip) ?: talk(tip)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(modifier = Modifier.weight(1f))

        VisorHeader()

        Text(
            text = "Welcome. Log in or sign up to get started.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
        )

        Spacer(modifier = Modifier.height(8.dp))

        VisorButton(
            text = stringResource(R.string.login_title),
            onClick = onLoginClick,
        )
        VisorButton(
            text = stringResource(R.string.sign_up_title),
            width = 0.75f,
            onClick = onSignUpClick,
        )

        Spacer(modifier = Modifier.height(16.dp))

        SelectableChip(
            label = if (profile.voiceNavigationEnabled) "Voice Navigation: On" else "Voice Navigation: Off",
            selected = profile.voiceNavigationEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val enabling = !profile.voiceNavigationEnabled
            viewModel.updateProfile(profile.copy(voiceNavigationEnabled = enabling))
            if (enabling) showVoiceTip = true
        }
        Text(
            text = "Say \"VISOR GO\" any time, then say a command — like \"log in\" or \"sign up\".",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.weight(1f))
    }

    if (showVoiceTip) {
        AlertDialog(
            onDismissRequest = { showVoiceTip = false },
            title = { Text("Voice navigation is on") },
            text = {
                Text(
                    "Say \"VISOR GO\", then say what you'd like. A few things to try:\n\n" +
                            "• \"log in\" or \"sign up\"\n" +
                            "• \"go home\"\n" +
                            "• \"help\"\n" +
                            "• \"open settings\"\n\n" +
                            "You can turn this off any time in Settings."
                )
            },
            confirmButton = {
                TextButton(onClick = { showVoiceTip = false }) { Text("Got it") }
            },
        )
    }
}
