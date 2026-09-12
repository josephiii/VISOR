package ucf.visor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import ucf.visor.ui.profile.UserProfile

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

    companion object {
        /** Maps the user's saved accessibility preference onto one of the four themes. */
        fun forProfile(profile: UserProfile, systemInDarkTheme: Boolean): AppTheme = when {
            profile.appHighContrast && systemInDarkTheme -> HighContrastDark
            profile.appHighContrast -> HighContrastLight
            systemInDarkTheme -> ClarityDark
            else -> ClarityLight
        }
    }
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

// NOTE: lightColorScheme()/darkColorScheme() default every role you don't pass
// to Compose's stock Material-baseline palette (an arbitrary purple/gray scale)
// rather than deriving it from the roles you do pass. Earlier, VISOR only set
// 8 of the ~20 roles views in this app rely on, so anything using outline,
// secondaryContainer, primaryContainer, surfaceVariant, errorContainer, or the
// nav bar's surfaceContainer silently rendered in that unrelated stock palette
// instead of one of VISOR's four vetted themes. Every role referenced anywhere
// in ucf.visor.ui is set explicitly below so all four themes are fully covered.
private fun colorSchemeFor(theme: AppTheme) = when (theme) {
    AppTheme.ClarityLight -> lightColorScheme(
        primary = ClarityLightColors.Primary,
        onPrimary = ClarityLightColors.OnPrimary,
        primaryContainer = ClarityLightColors.PrimaryContainer,
        onPrimaryContainer = ClarityLightColors.OnPrimaryContainer,
        secondary = ClarityLightColors.Secondary,
        onSecondary = ClarityLightColors.OnSecondary,
        secondaryContainer = ClarityLightColors.SecondaryContainer,
        onSecondaryContainer = ClarityLightColors.OnSecondaryContainer,
        tertiary = ClarityLightColors.Secondary,
        onTertiary = ClarityLightColors.OnSecondary,
        tertiaryContainer = ClarityLightColors.SecondaryContainer,
        onTertiaryContainer = ClarityLightColors.OnSecondaryContainer,
        background = ClarityLightColors.Background,
        onBackground = ClarityLightColors.Text,
        surface = ClarityLightColors.Surface,
        onSurface = ClarityLightColors.Text,
        surfaceVariant = ClarityLightColors.SurfaceVariant,
        onSurfaceVariant = ClarityLightColors.OnSurfaceVariant,
        surfaceContainer = ClarityLightColors.SurfaceContainer,
        outline = ClarityLightColors.Outline,
        outlineVariant = ClarityLightColors.OutlineVariant,
        error = StatusColors.ErrorLight,
        onError = StatusColors.OnErrorLight,
        errorContainer = StatusColors.ErrorLight,
        onErrorContainer = StatusColors.OnErrorLight,
        inverseSurface = ClarityLightColors.Text,
        inverseOnSurface = ClarityLightColors.Background,
        inversePrimary = ClarityDarkColors.Primary,
        scrim = Color.Black,
        surfaceTint = ClarityLightColors.Primary,
    )

    AppTheme.ClarityDark -> darkColorScheme(
        primary = ClarityDarkColors.Primary,
        onPrimary = ClarityDarkColors.OnPrimary,
        primaryContainer = ClarityDarkColors.PrimaryContainer,
        onPrimaryContainer = ClarityDarkColors.OnPrimaryContainer,
        secondary = ClarityDarkColors.Secondary,
        onSecondary = ClarityDarkColors.OnSecondary,
        secondaryContainer = ClarityDarkColors.SecondaryContainer,
        onSecondaryContainer = ClarityDarkColors.OnSecondaryContainer,
        tertiary = ClarityDarkColors.Secondary,
        onTertiary = ClarityDarkColors.OnSecondary,
        tertiaryContainer = ClarityDarkColors.SecondaryContainer,
        onTertiaryContainer = ClarityDarkColors.OnSecondaryContainer,
        background = ClarityDarkColors.Background,
        onBackground = ClarityDarkColors.Text,
        surface = ClarityDarkColors.Surface,
        onSurface = ClarityDarkColors.Text,
        surfaceVariant = ClarityDarkColors.SurfaceVariant,
        onSurfaceVariant = ClarityDarkColors.OnSurfaceVariant,
        surfaceContainer = ClarityDarkColors.SurfaceContainer,
        outline = ClarityDarkColors.Outline,
        outlineVariant = ClarityDarkColors.OutlineVariant,
        error = StatusColors.ErrorDark,
        onError = StatusColors.OnErrorDark,
        errorContainer = StatusColors.ErrorDark,
        onErrorContainer = StatusColors.OnErrorDark,
        inverseSurface = ClarityDarkColors.Text,
        inverseOnSurface = ClarityDarkColors.Background,
        inversePrimary = ClarityLightColors.Primary,
        scrim = Color.Black,
        surfaceTint = ClarityDarkColors.Primary,
    )

    AppTheme.HighContrastLight -> lightColorScheme(
        primary = HighContrastLightColors.Primary,
        onPrimary = HighContrastLightColors.OnPrimary,
        primaryContainer = HighContrastLightColors.PrimaryContainer,
        onPrimaryContainer = HighContrastLightColors.OnPrimaryContainer,
        secondary = HighContrastLightColors.Secondary,
        onSecondary = HighContrastLightColors.OnSecondary,
        secondaryContainer = HighContrastLightColors.SecondaryContainer,
        onSecondaryContainer = HighContrastLightColors.OnSecondaryContainer,
        tertiary = HighContrastLightColors.Secondary,
        onTertiary = HighContrastLightColors.OnSecondary,
        tertiaryContainer = HighContrastLightColors.SecondaryContainer,
        onTertiaryContainer = HighContrastLightColors.OnSecondaryContainer,
        background = HighContrastLightColors.Background,
        onBackground = HighContrastLightColors.Text,
        surface = HighContrastLightColors.Surface,
        onSurface = HighContrastLightColors.Text,
        surfaceVariant = HighContrastLightColors.SurfaceVariant,
        onSurfaceVariant = HighContrastLightColors.OnSurfaceVariant,
        surfaceContainer = HighContrastLightColors.SurfaceContainer,
        outline = HighContrastLightColors.Outline,
        outlineVariant = HighContrastLightColors.OutlineVariant,
        error = StatusColors.ErrorLight,
        onError = StatusColors.OnErrorLight,
        errorContainer = StatusColors.ErrorLight,
        onErrorContainer = StatusColors.OnErrorLight,
        inverseSurface = HighContrastLightColors.Text,
        inverseOnSurface = HighContrastLightColors.Background,
        inversePrimary = HighContrastDarkColors.Primary,
        scrim = Color.Black,
        surfaceTint = HighContrastLightColors.Primary,
    )

    AppTheme.HighContrastDark -> darkColorScheme(
        primary = HighContrastDarkColors.Primary,
        onPrimary = HighContrastDarkColors.OnPrimary,
        primaryContainer = HighContrastDarkColors.PrimaryContainer,
        onPrimaryContainer = HighContrastDarkColors.OnPrimaryContainer,
        secondary = HighContrastDarkColors.Secondary,
        onSecondary = HighContrastDarkColors.OnSecondary,
        secondaryContainer = HighContrastDarkColors.SecondaryContainer,
        onSecondaryContainer = HighContrastDarkColors.OnSecondaryContainer,
        tertiary = HighContrastDarkColors.Secondary,
        onTertiary = HighContrastDarkColors.OnSecondary,
        tertiaryContainer = HighContrastDarkColors.SecondaryContainer,
        onTertiaryContainer = HighContrastDarkColors.OnSecondaryContainer,
        background = HighContrastDarkColors.Background,
        onBackground = HighContrastDarkColors.Text,
        surface = HighContrastDarkColors.Surface,
        onSurface = HighContrastDarkColors.Text,
        surfaceVariant = HighContrastDarkColors.SurfaceVariant,
        onSurfaceVariant = HighContrastDarkColors.OnSurfaceVariant,
        surfaceContainer = HighContrastDarkColors.SurfaceContainer,
        outline = HighContrastDarkColors.Outline,
        outlineVariant = HighContrastDarkColors.OutlineVariant,
        error = StatusColors.ErrorDark,
        onError = StatusColors.OnErrorDark,
        errorContainer = StatusColors.ErrorDark,
        onErrorContainer = StatusColors.OnErrorDark,
        inverseSurface = HighContrastDarkColors.Text,
        inverseOnSurface = HighContrastDarkColors.Background,
        inversePrimary = HighContrastLightColors.Primary,
        scrim = Color.Black,
        surfaceTint = HighContrastDarkColors.Primary,
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

private fun TextStyle.scaled(factor: Float): TextStyle = copy(
    fontSize = fontSize.scaled(factor),
    lineHeight = lineHeight.scaled(factor),
)

private fun TextUnit.scaled(factor: Float): TextUnit =
    if (type == TextUnitType.Sp) this * factor else this

/**
 * Scales every role in [Typography] by [factor] so a user's text-size
 * preference (see [UserProfile.textScale]) applies consistently everywhere
 * text uses `MaterialTheme.typography.*` — Section 508 calls for text to be
 * resizable up to 200% without losing content or functionality.
 */
private fun Typography.scaled(factor: Float): Typography = Typography(
    displayLarge = displayLarge.scaled(factor),
    displayMedium = displayMedium.scaled(factor),
    displaySmall = displaySmall.scaled(factor),
    headlineLarge = headlineLarge.scaled(factor),
    headlineMedium = headlineMedium.scaled(factor),
    headlineSmall = headlineSmall.scaled(factor),
    titleLarge = titleLarge.scaled(factor),
    titleMedium = titleMedium.scaled(factor),
    titleSmall = titleSmall.scaled(factor),
    bodyLarge = bodyLarge.scaled(factor),
    bodyMedium = bodyMedium.scaled(factor),
    bodySmall = bodySmall.scaled(factor),
    labelLarge = labelLarge.scaled(factor),
    labelMedium = labelMedium.scaled(factor),
    labelSmall = labelSmall.scaled(factor),
)

// @param appTheme which of the four VISOR themes to render. Defaults to
//       Clarity Light/Dark based on the system setting, but is normally
//       driven by the user's saved accessibility preference — see
//       AppTheme.forProfile — so High Contrast can be picked independently
//       of the OS theme.
// @param textScale multiplier applied on top of the base type scale (1.0 =
//       standard). Comes from the user's Settings > Text size preference.
@Composable
fun VisorTheme(
    appTheme: AppTheme = if (isSystemInDarkTheme()) AppTheme.ClarityDark else AppTheme.ClarityLight,
    textScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val colorScheme = colorSchemeFor(appTheme)
    val statusColors = statusColorsFor(appTheme)
    val typography = if (textScale == 1f) Typography else Typography.scaled(textScale)

    CompositionLocalProvider(LocalVisorStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
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
