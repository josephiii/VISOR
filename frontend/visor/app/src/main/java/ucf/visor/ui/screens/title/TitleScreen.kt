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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ucf.visor.R
import ucf.visor.ui.components.VisorButton
import ucf.visor.ui.components.VisorHeader
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.ui.viewmodel.VisorViewModel

/**
 * The very first screen a signed-out user sees — VISOR's front door.
 *
 * Purpose is narrow by design: get to Log In or Sign Up. Voice navigation is
 * on from the start (it is turned off in Settings, not here), so while it is
 * on, a line under the buttons says how to use it. Nothing here asks for
 * credentials, so unlike the screens behind it, everything on this screen is
 * safe to also drive by voice.
 *
 * A returning, already-logged-in user never sees this screen at all — see
 * VisorViewModel.startDestination.
 */
@Composable
fun TitleScreen(
    viewModel: VisorViewModel,
    onLoginClick: () -> Unit,
    onSignUpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

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

        // Only true while it is: someone who turned voice navigation off in
        // Settings, then logged out, isn't told to say "VISOR GO".
        if (profile.voiceNavigationEnabled) {
            Text(
                text = stringResource(R.string.title_voice_hint),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}
