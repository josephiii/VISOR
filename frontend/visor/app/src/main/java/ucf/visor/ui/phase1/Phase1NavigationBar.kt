package ucf.visor.ui.phase1

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ucf.visor.R
import ucf.visor.ui.viewmodel.VisorViewModel

@Composable
fun Phase1NavigationBar(
    currentRoute: String?,
    viewModel: VisorViewModel
) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        contentColor = MaterialTheme.colorScheme.contentColorFor(
            NavigationBarDefaults.containerColor
        )
    ) {
        NavigationBarItem(
            selected = currentRoute == "p1_test_mode_config",
            onClick = { viewModel.configure() },
            icon = {
                Icon(
                    Icons.Default.Adjust,
                    contentDescription = "Configure VISOR Mode"
                )
            },
            label = { Text(stringResource(R.string.p1_mode_navbar_title)) }
        )
        NavigationBarItem(
            selected = currentRoute == "home",
            onClick = { viewModel.home() },
            icon = {
                Icon(
                    Icons.Default.Home,
                    contentDescription = "Home"
                )
            },
            label = { Text(stringResource(R.string.home_screen_title)) }
        )
        NavigationBarItem(
            selected = currentRoute == "hardware_pairing",
            onClick = { viewModel.hardwarePairing() },
            icon = {
                Icon(
                    Icons.Default.Bluetooth,
                    contentDescription = "Hardware"
                )
            },
            label = { Text(stringResource(R.string.hardware_pairing_navbar_title)) }
        )
    }
}