package ucf.visor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// The four accessibility-first themes VISOR supports:
// ClarityLight (default)
// ClarityDark
// HighContrastLight
// HighContrastDark
enum class AppTheme {
    ClarityLight,
    ClarityDark,
    HighContrastLight,
    HighContrastDark;

    val isDark: Boolean
        get() = this == ClarityDark || this == HighContrastDark
}


// Material3's ColorScheme has no built-in "success" or "warning" role, so
// status colors are carried separately via CompositionLocal and pulled in
// alongside MaterialTheme.colorScheme. Access with VisorTheme.statusColors
// inside any @Composable.

data class VisorStatusColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val error: Color,
    val onError: Color
)

private val LocalVisorStatusColors = staticCompositionLocalOf {
    VisorStatusColors(
        success = StatusColors.SuccessLight,
        onSuccess = StatusColors.OnSuccessLight,
        warning = StatusColors.WarningLight,
        onWarning = StatusColors.OnWarningLight,
        error = StatusColors.ErrorLight,
        onError = StatusColors.OnErrorLight
    )
}

private fun colorSchemeFor(theme: AppTheme) = when (theme) {
    AppTheme.ClarityLight -> lightColorScheme(
        primary = ClarityLightColors.Primary,
        onPrimary = ClarityLightColors.OnPrimary,
        secondary = ClarityLightColors.Secondary,
        onSecondary = ClarityLightColors.OnSecondary,
        background = ClarityLightColors.Background,
        onBackground = ClarityLightColors.Text,
        surface = ClarityLightColors.Surface,
        onSurface = ClarityLightColors.Text,
        error = StatusColors.ErrorLight,
        onError = StatusColors.OnErrorLight
    )

    AppTheme.ClarityDark -> darkColorScheme(
        primary = ClarityDarkColors.Primary,
        onPrimary = ClarityDarkColors.OnPrimary,
        secondary = ClarityDarkColors.Secondary,
        onSecondary = ClarityDarkColors.OnSecondary,
        background = ClarityDarkColors.Background,
        onBackground = ClarityDarkColors.Text,
        surface = ClarityDarkColors.Surface,
        onSurface = ClarityDarkColors.Text,
        error = StatusColors.ErrorDark,
        onError = StatusColors.OnErrorDark
    )

    AppTheme.HighContrastLight -> lightColorScheme(
        primary = HighContrastLightColors.Primary,
        onPrimary = HighContrastLightColors.OnPrimary,
        secondary = HighContrastLightColors.Secondary,
        onSecondary = HighContrastLightColors.OnSecondary,
        background = HighContrastLightColors.Background,
        onBackground = HighContrastLightColors.Text,
        surface = HighContrastLightColors.Surface,
        onSurface = HighContrastLightColors.Text,
        error = StatusColors.ErrorLight,
        onError = StatusColors.OnErrorLight
    )

    AppTheme.HighContrastDark -> darkColorScheme(
        primary = HighContrastDarkColors.Primary,
        onPrimary = HighContrastDarkColors.OnPrimary,
        secondary = HighContrastDarkColors.Secondary,
        onSecondary = HighContrastDarkColors.OnSecondary,
        background = HighContrastDarkColors.Background,
        onBackground = HighContrastDarkColors.Text,
        surface = HighContrastDarkColors.Surface,
        onSurface = HighContrastDarkColors.Text,
        error = StatusColors.ErrorDark,
        onError = StatusColors.OnErrorDark
    )
}

private fun statusColorsFor(theme: AppTheme) = if (theme.isDark) {
    VisorStatusColors(
        success = StatusColors.SuccessDark,
        onSuccess = StatusColors.OnSuccessDark,
        warning = StatusColors.WarningDark,
        onWarning = StatusColors.OnWarningDark,
        error = StatusColors.ErrorDark,
        onError = StatusColors.OnErrorDark
    )
} else {
    VisorStatusColors(
        success = StatusColors.SuccessLight,
        onSuccess = StatusColors.OnSuccessLight,
        warning = StatusColors.WarningLight,
        onWarning = StatusColors.OnWarningLight,
        error = StatusColors.ErrorLight,
        onError = StatusColors.OnErrorLight
    )
}

// @param appTheme which of the four VISOR themes to render. Defaults to
//       Clarity Light/Dark based on the system setting, but should
//       ultimately be driven by a user-facing accessibility preference
//       (persisted in settings) so someone can pick High Contrast
//       independently of their OS theme.
@Composable
fun VisorTheme(
    appTheme: AppTheme = if (isSystemInDarkTheme()) AppTheme.ClarityDark else AppTheme.ClarityLight,
    content: @Composable () -> Unit
) {
    val colorScheme = colorSchemeFor(appTheme)
    val statusColors = statusColorsFor(appTheme)

    CompositionLocalProvider(LocalVisorStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// Convenience accessor: VisorTheme.statusColors.success, etc.
object VisorTheme {
    val statusColors: VisorStatusColors
        @Composable
        get() = LocalVisorStatusColors.current
}
