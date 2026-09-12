package ucf.visor.ui.theme

import androidx.compose.ui.graphics.Color

// Each theme below defines every Material3 ColorScheme role VISOR actually
// references (see colorSchemeFor in Theme.kt) rather than leaving them at
// Compose's stock baseline defaults. Every pairing here (container/on-container,
// surfaceVariant/onSurfaceVariant, outline against background/surface) has been
// checked against WCAG contrast targets: 4.5:1 for text pairs, 3:1 for the
// outline/UI-component pairs. See documentation/research/section-508-accessibility-guidelines.md.

object ClarityLightColors {
    val Background = Color(0xFFFFFFFF)
    val Surface = Color(0xFFEAF1F9)
    val Text = Color(0xFF15233D)
    val Primary = Color(0xFF1A4D98)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFD7E6FA)
    val OnPrimaryContainer = Color(0xFF0A2E57)
    val Secondary = Color(0xFF0B5755)
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFFD1EEEA)
    val OnSecondaryContainer = Color(0xFF052E2B)
    val SurfaceVariant = Color(0xFFDCE6F2)
    val OnSurfaceVariant = Color(0xFF3C4A5E)
    val SurfaceContainer = Color(0xFFF1F5FA)
    val Outline = Color(0xFF56657C)
    val OutlineVariant = Color(0xFFC6D2E1)
}

object ClarityDarkColors {
    val Background = Color(0xFF0B1220)
    val Surface = Color(0xFF15233D)
    val Text = Color(0xFFF4F8FC)
    val Primary = Color(0xFF8FBBF7)
    val OnPrimary = Color(0xFF0B1220)
    val PrimaryContainer = Color(0xFF16345E)
    val OnPrimaryContainer = Color(0xFFD7E6FA)
    val Secondary = Color(0xFF5FE0D3)
    val OnSecondary = Color(0xFF0B1220)
    val SecondaryContainer = Color(0xFF0E3B38)
    val OnSecondaryContainer = Color(0xFFCFF3EE)
    val SurfaceVariant = Color(0xFF1D2C48)
    val OnSurfaceVariant = Color(0xFFC3D0E3)
    val SurfaceContainer = Color(0xFF101A2E)
    val Outline = Color(0xFF7C8CA8)
    val OutlineVariant = Color(0xFF33415C)
}

object HighContrastLightColors {
    val Background = Color(0xFFFFFFFF)
    val Surface = Color(0xFFFFFFFF)
    val Text = Color(0xFF000000)
    val Primary = Color(0xFF0E2A47)
    val OnPrimary = Color(0xFFFFFFFF)
    // High contrast: selected/filled states use the solid primary/secondary
    // tone directly (no pastel tint) so the on/off state is unmistakable.
    val PrimaryContainer = Primary
    val OnPrimaryContainer = OnPrimary
    val Secondary = Color(0xFF083D3B)
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Secondary
    val OnSecondaryContainer = OnSecondary
    val SurfaceVariant = Color(0xFFF0F0F0)
    val OnSurfaceVariant = Color(0xFF000000)
    val SurfaceContainer = Color(0xFFF2F2F2)
    val Outline = Color(0xFF000000)
    val OutlineVariant = Color(0xFF4D4D4D)
}

object HighContrastDarkColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF000000)
    val Text = Color(0xFFFFFFFF)
    val Primary = Color(0xFF9CC7FF)
    val OnPrimary = Color(0xFF000000)
    val PrimaryContainer = Primary
    val OnPrimaryContainer = OnPrimary
    val Secondary = Color(0xFFFFD400)
    val OnSecondary = Color(0xFF000000)
    val SecondaryContainer = Secondary
    val OnSecondaryContainer = OnSecondary
    val SurfaceVariant = Color(0xFF1A1A1A)
    val OnSurfaceVariant = Color(0xFFFFFFFF)
    val SurfaceContainer = Color(0xFF0D0D0D)
    val Outline = Color(0xFFFFFFFF)
    val OutlineVariant = Color(0xFFB3B3B3)
}

object StatusColors {
    // Light mode
    val SuccessLight = Color(0xFF0B5755)
    val OnSuccessLight = Color(0xFFFFFFFF)
    val ErrorLight = Color(0xFF8D320A)
    val OnErrorLight = Color(0xFFFFFFFF)
    val WarningLight = Color(0xFF6B4700)
    val OnWarningLight = Color(0xFFFFFFFF)

    // Dark mode
    val SuccessDark = Color(0xFF5FE0D3)
    val OnSuccessDark = Color(0xFF0B1220)
    val ErrorDark = Color(0xFFFF8A5C)
    val OnErrorDark = Color(0xFF0B1220)
    val WarningDark = Color(0xFFFFC94D)
    val OnWarningDark = Color(0xFF0B1220)
}
