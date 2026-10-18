package ucf.visor.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import ucf.visor.R
import ucf.visor.ui.feedback.LocalHaptics
import ucf.visor.ui.viewmodel.VisorViewModel

@Composable
fun VisorNavigationBar(
    currentRoute: String?,
    viewModel: VisorViewModel
) {
    NavigationBar(modifier = Modifier.fillMaxWidth()) {
        VisorNavigationItem(
            // Help is opened from Settings, so Settings stays the tab shown as here.
            selected = currentRoute == "settings" || currentRoute == "help",
            onClick = { viewModel.settings() },
            icon = Icons.Default.Settings,
            label = stringResource(R.string.settings_navbar_title),
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

/**
 * One tab of a navigation bar, its selection plain to see: Material's pale
 * pill was 1.12:1 against the bar, and its selected label hardly differed from
 * the others. Selected, the icon sits white-on-primary in a solid pill (as
 * strong as a filled button), and the label is bold and underlined.
 *
 * The tab is named by its label alone, so a screen reader says what the screen
 * shows ("Pair", not the icon's name).
 */
@Composable
fun RowScope.VisorNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    val style = MaterialTheme.typography.labelMedium
    NavigationBarItem(
        selected = selected,
        onClick = {
            haptics?.tick()
            onClick()
        },
        icon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(iconSizeFor(style)))
        },
        label = {
            Text(
                label,
                style = style,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                textDecoration = if (selected) TextDecoration.Underline else null,
                textAlign = TextAlign.Center,
            )
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = colors.onPrimary,
            indicatorColor = colors.primary,
            selectedTextColor = colors.onSurface,
            unselectedIconColor = colors.onSurfaceVariant,
            unselectedTextColor = colors.onSurfaceVariant,
        ),
    )
}
