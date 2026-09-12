package ucf.visor.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ucf.visor.R
import ucf.visor.ui.viewmodel.VisorViewModel

@Composable
fun VisorNavigationBar(
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
            selected = currentRoute == "settings",
            onClick = { viewModel.settings() },
            icon = {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Profile Settings"
                )
            },
            label = { AutoSizeText(stringResource(R.string.user_profile_navbar_title), style = MaterialTheme.typography.labelMedium) }
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
            label = { AutoSizeText(stringResource(R.string.home_screen_title), style = MaterialTheme.typography.labelMedium) }
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
            label = { AutoSizeText(stringResource(R.string.hardware_pairing_navbar_title), style = MaterialTheme.typography.labelMedium) }
        )
    }
}