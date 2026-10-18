package ucf.visor.ui.screens.phase1

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.NavigationBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ucf.visor.R
import ucf.visor.ui.components.VisorNavigationItem
import ucf.visor.ui.viewmodel.VisorViewModel

@Composable
fun Phase1NavigationBar(
    currentRoute: String?,
    viewModel: VisorViewModel
) {
    NavigationBar(modifier = Modifier.fillMaxWidth()) {
        VisorNavigationItem(
            selected = currentRoute == "p1_test_mode_config",
            onClick = { viewModel.configure() },
            icon = Icons.Default.Adjust,
            label = stringResource(R.string.p1_mode_navbar_title),
        )
        VisorNavigationItem(
            selected = currentRoute == "home",
            onClick = { viewModel.home() },
            icon = Icons.Default.Home,
            label = stringResource(R.string.home_screen_title),
        )
        VisorNavigationItem(
            selected = currentRoute == "hardware_pairing",
            onClick = { viewModel.hardwarePairing() },
            icon = Icons.Default.Bluetooth,
            label = stringResource(R.string.hardware_pairing_navbar_title),
        )
    }
}
