package ucf.visor.ui.screens.hardware

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meta.wearable.dat.core.types.RegistrationState
import ucf.visor.R
import ucf.visor.ui.components.SelectableChip
import ucf.visor.ui.components.SwitchButton
import ucf.visor.ui.components.TipItem
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.ui.theme.VisorShapes
import ucf.visor.ui.viewmodel.VisorViewModel
import ucf.visor.wearables.GlassesStatus

// This screen will prompt the user to register VISOR with their glasses, and —
// once registered — shows the glasses' live state (MWDAT 1.0 device state).
// It is also where VISOR's glasses can be disconnected, and where an update
// the glasses need before VISOR can use them is offered.
@Composable
fun HardwarePairingScreen(
    viewModel: VisorViewModel,
    modifier: Modifier = Modifier,
    talk: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Registration and updates hand off to the Meta AI app, which needs an Activity.
    val withActivity: ((Activity) -> Unit) -> Unit = { action ->
        activity?.let(action)
            ?: Toast.makeText(context, "Activity not available", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline)
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
                    title = stringResource(R.string.environment_capture_title),
                    text = stringResource(R.string.environment_capture),
                )
            }
        }

        uiState.glassesStatus?.takeIf { uiState.isRegistered }?.let { status ->
            GlassesStatusCard(status = status, onSpeak = { talk(status.spokenSummary()) })
        }

        if (uiState.isFirmwareUpdateRequired || uiState.isDatAppUpdateRequired) {
            UpdateRequiredCard(
                firmware = uiState.isFirmwareUpdateRequired,
                glassesApp = uiState.isDatAppUpdateRequired,
                onUpdateFirmware = { withActivity(viewModel::openFirmwareUpdate) },
                onUpdateGlassesApp = { withActivity(viewModel::openDATGlassesAppUpdate) },
            )
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
                onClick = { withActivity(viewModel::startRegistration) },
            )
            if (uiState.isRegistered) {
                SwitchButton(
                    label = stringResource(R.string.unregister_button_title),
                    isDestructive = true,
                    enabled = uiState.registrationState == RegistrationState.REGISTERED,
                    onClick = { withActivity(viewModel::startUnregistration) },
                )
            }
        }
    }
}

/**
 * The glasses need an update before VISOR can use them, and the button that
 * opens the right page of the Meta AI app. The message is a live region so a
 * screen reader announces it as soon as it appears.
 */
@Composable
private fun UpdateRequiredCard(
    firmware: Boolean,
    glassesApp: Boolean,
    onUpdateFirmware: () -> Unit,
    onUpdateGlassesApp: () -> Unit,
) {
    val message = when {
        firmware && glassesApp -> stringResource(R.string.update_required_both_message)
        firmware -> stringResource(R.string.update_required_firmware_message)
        else -> stringResource(R.string.update_required_dat_app_message)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = VisorShapes.Control,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = Icons.Default.Warning, contentDescription = null)
                Text(
                    stringResource(R.string.update_required_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (firmware) {
                SwitchButton(
                    label = stringResource(R.string.update_firmware_button_title),
                    onClick = onUpdateFirmware,
                )
            }
            if (glassesApp) {
                SwitchButton(
                    label = stringResource(R.string.update_dat_app_button_title),
                    onClick = onUpdateGlassesApp,
                )
            }
        }
    }
}

/**
 * The glasses' live state, readable at a glance and — more to the point for
 * VISOR's users — aloud: checking battery without finding and reading the
 * charging case's light is exactly the kind of small task low vision makes hard.
 */
@Composable
private fun GlassesStatusCard(status: GlassesStatus, onSpeak: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = VisorShapes.Control,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "${status.model}: ${if (status.connected) "connected" else "not connected"}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            val details = buildList {
                status.batteryPercent?.let {
                    add("Battery $it%" + if (status.charging == true) ", charging" else "")
                }
                status.worn?.let { add(if (it) "Being worn" else "Not being worn") }
                status.temperatureWord?.let { add("Temperature $it") }
            }
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
            }
            SelectableChip(
                label = "Read glasses status aloud",
                selected = false,
                showCheckmark = false,
                modifier = Modifier.fillMaxWidth(),
                onClick = onSpeak,
            )
        }
    }
}