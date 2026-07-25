package ucf.visor.ui.screens

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.tooling.preview.Preview

import ucf.visor.R
import ucf.visor.ui.components.SwitchButton
import ucf.visor.ui.components.TipItem
import ucf.visor.wearables.WearablesViewModel

// This screen will prompt the user to
@Composable
fun HomeScreen(
    viewModel: WearablesViewModel,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val context = LocalContext.current

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(all = 24.dp)
                .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.camera_access_icon),
                contentDescription = stringResource(R.string.camera_access_icon_description),
                modifier = Modifier.size(80.dp * LocalDensity.current.density),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
            ) {
                TipItem(
                    iconResId = R.drawable.smart_glasses_icon,
                    title = stringResource(R.string.home_tip_video_title),
                    text = stringResource(R.string.home_tip_video),
                )
                TipItem(
                    iconResId = R.drawable.sound_icon,
                    title = stringResource(R.string.home_tip_audio_title),
                    text = stringResource(R.string.home_tip_audio),
                )
                TipItem(
                    iconResId = R.drawable.walking_icon,
                    title = stringResource(R.string.home_tip_hands_title),
                    text = stringResource(R.string.home_tip_hands),
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))

        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // App Registration Button
            Text(
                text = stringResource(R.string.home_redirect_message),
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            SwitchButton(
                label = stringResource(R.string.register_button_title),
                enabled = uiState.canStartRegistration,
                onClick = {
                    activity?.let { viewModel.startRegistration(it) }
                        ?: Toast.makeText(context, "Activity not available", Toast.LENGTH_SHORT)
                            .show()
                },
            )
        }
    }
}