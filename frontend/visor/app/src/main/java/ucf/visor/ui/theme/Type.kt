package ucf.visor.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import ucf.visor.R

// Using Atkinson Hyperlegible Next: https://fonts.google.com/specimen/Atkinson+Hyperlegible+Next

val AccessibleFontFamily = FontFamily(
    Font(R.font.atkinson_hyperlegible_next_bold, FontWeight.Bold),
    Font(R.font.atkinson_hyperlegible_next_bolditalic, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.atkinson_hyperlegible_next_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.atkinson_hyperlegible_next_medium, FontWeight.Medium),
    Font(R.font.atkinson_hyperlegible_next_mediumitalic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.atkinson_hyperlegible_next_regular, FontWeight.Normal),
    Font(R.font.atkinson_hyperlegible_next_semibold, FontWeight.SemiBold),
    Font(R.font.atkinson_hyperlegible_next_semibolditalic, FontWeight.SemiBold, FontStyle.Italic)
)

private val platformTextStyle = PlatformTextStyle(includeFontPadding = false)
private val lineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun accessibleTextStyle(
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    letterSpacing: androidx.compose.ui.unit.TextUnit
) = TextStyle(
    fontFamily = AccessibleFontFamily,
    fontWeight = fontWeight,
    fontSize = fontSize,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
    textAlign = TextAlign.Start,
    hyphens = Hyphens.None,
    platformStyle = platformTextStyle,
    lineHeightStyle = lineHeightStyle
)

val Typography = Typography(
    displayLarge = accessibleTextStyle(40.sp, 60.sp, FontWeight.Normal, 0.sp),
    displayMedium = accessibleTextStyle(34.sp, 51.sp, FontWeight.Normal, 0.sp),
    displaySmall = accessibleTextStyle(28.sp, 42.sp, FontWeight.Normal, 0.sp),

    headlineLarge = accessibleTextStyle(30.sp, 45.sp, FontWeight.SemiBold, 0.sp),
    headlineMedium = accessibleTextStyle(26.sp, 39.sp, FontWeight.SemiBold, 0.sp),
    headlineSmall = accessibleTextStyle(22.sp, 33.sp, FontWeight.SemiBold, 0.sp),

    titleLarge = accessibleTextStyle(20.sp, 30.sp, FontWeight.SemiBold, 0.15.sp),
    titleMedium = accessibleTextStyle(18.sp, 27.sp, FontWeight.Medium, 0.15.sp),
    titleSmall = accessibleTextStyle(16.sp, 24.sp, FontWeight.Medium, 0.1.sp),

    bodyLarge = accessibleTextStyle(18.sp, 28.sp, FontWeight.Normal, 0.3.sp),
    bodyMedium = accessibleTextStyle(16.sp, 24.sp, FontWeight.Normal, 0.25.sp),
    bodySmall = accessibleTextStyle(14.sp, 22.sp, FontWeight.Normal, 0.4.sp),

    labelLarge = accessibleTextStyle(16.sp, 24.sp, FontWeight.Medium, 0.3.sp),
    labelMedium = accessibleTextStyle(14.sp, 22.sp, FontWeight.Medium, 0.3.sp),
    labelSmall = accessibleTextStyle(14.sp, 22.sp, FontWeight.Medium, 0.4.sp)
    // Material3's stock labelSmall is 11sp - raised to 14sp here since
    // this app shouldn't ship any text smaller than that by default.
)
