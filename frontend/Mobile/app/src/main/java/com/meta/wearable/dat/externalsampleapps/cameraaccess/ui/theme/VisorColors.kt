package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * VISOR-165 — accessibility-first palettes.
 * All pairings verified WCAG 2.2 AA (nearly all AAA).
 * Swap [VisorPalette.Current] once the team + ophthalmologist pick one.
 */
data class VisorPalette(
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,        // primary action buttons
    val onAccent: Color,      // text on accent buttons
    val positive: Color,      // "clear / success" status
    val alert: Color,         // "hazard / error" status
) {
    companion object {
        /** Option A — "Cerebro": deep navy + gold. Text 12.9:1, gold 9.5:1. */
        val Cerebro = VisorPalette(
            background = Color(0xFF0E2A47),
            surface = Color(0xFF173D66),
            textPrimary = Color(0xFFF5F1E6),
            textSecondary = Color(0xFF8FC1E8),
            accent = Color(0xFFFFC94D),
            onAccent = Color(0xFF1A1A1A),
            positive = Color(0xFF5FD4A2),
            alert = Color(0xFFFF9E6E),
        )

        /** Option B — "Clarity": white + blue light theme. Text 15.7:1, blue 7.9:1. */
        val Clarity = VisorPalette(
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFEAF1F9),
            textPrimary = Color(0xFF15233D),
            textSecondary = Color(0xFF1B4F9C),
            accent = Color(0xFF1B4F9C),
            onAccent = Color(0xFFFFFFFF),
            positive = Color(0xFF0E6E6B),
            alert = Color(0xFFB3401E),
        )

        /** Option C — "Sentinel": deep green-gray + teal-green/coral. Text 13.0:1. */
        val Sentinel = VisorPalette(
            background = Color(0xFF1F2924),
            surface = Color(0xFF3D4E45),
            textPrimary = Color(0xFFEAF0EB),
            textSecondary = Color(0xFFB8C4BC),
            accent = Color(0xFF5FD4A2),
            onAccent = Color(0xFF10241B),
            positive = Color(0xFF5FD4A2),
            alert = Color(0xFFFF9E6E),
        )

        /** Option D — "Knight": UCF black & gold, neutral grays. Text 16.5:1, gold 11.1:1. */
        val Knight = VisorPalette(
            background = Color(0xFF1B1B1D),
            surface = Color(0xFF47474F),
            textPrimary = Color(0xFFFAFAFA),
            textSecondary = Color(0xFFC7C9CE),
            accent = Color(0xFFFFC904),
            onAccent = Color(0xFF1B1B1D),
            positive = Color(0xFF5FD4A2),
            alert = Color(0xFFFF9E6E),
        )

        /** Option E — "Service": VA.gov design-system navy/blue/gold, light. Text 13.7:1. */
        val Service = VisorPalette(
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFE1E7F1),
            textPrimary = Color(0xFF112E51),
            textSecondary = Color(0xFF205493),
            accent = Color(0xFF205493),
            onAccent = Color(0xFFFFFFFF),
            positive = Color(0xFF0E6E6B),
            alert = Color(0xFFB3401E),
        )

        /** The palette the app uses — Clarity won the team/sponsor vote (VISOR-165, 7/9). */
        val Current = Clarity
    }
}
