package ucf.visor.ui.screens.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import ucf.visor.data.VoiceCategory
import ucf.visor.ui.components.SwitchChip
import ucf.visor.ui.components.VisorButton
import ucf.visor.ui.components.VisorDialog
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
    talk: (String, VoiceCategory) -> Unit = { _, _ -> },
    voiceNav: VoiceNavigationController? = null,
    onLoginClick: () -> Unit,
    onSignUpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    var showVoiceTip by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val spokenTip = stringResource(R.string.title_voice_tip_spoken)

    LaunchedEffect(showVoiceTip) {
        // Said with the wake-phrase listener paused (speakGuarded): the tip
        // says "VISOR GO", which VISOR would otherwise hear itself say.
        if (showVoiceTip) voiceNav?.speakGuarded(spokenTip) ?: talk(spokenTip, VoiceCategory.VOICE_COMMANDS)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline)
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState)
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(modifier = Modifier.weight(1f))

        VisorHeader()

        Text(
            text = stringResource(R.string.title_welcome),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
        )

        VisorButton(
            text = stringResource(R.string.login_title),
            onClick = onLoginClick,
        )
        VisorButton(
            text = stringResource(R.string.sign_up_title),
            width = 0.75f,
            onClick = onSignUpClick,
        )

        SwitchChip(
            title = stringResource(R.string.settings_section_voice_navigation),
            on = profile.voiceNavigationEnabled,
            onToggle = {
                val enabling = !profile.voiceNavigationEnabled
                viewModel.updateProfile(profile.copy(voiceNavigationEnabled = enabling))
                if (enabling) showVoiceTip = true
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.title_voice_hint),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.weight(1f))
    }

    // Not given talk: the tip is already said, above, with the listener paused.
    if (showVoiceTip) {
        VisorDialog(
            title = stringResource(R.string.title_voice_tip_title),
            message = stringResource(R.string.title_voice_tip_message),
            confirmLabel = stringResource(R.string.got_it),
            dismissLabel = null,
            onConfirm = { showVoiceTip = false },
            onDismiss = { showVoiceTip = false },
        )
    }
}
