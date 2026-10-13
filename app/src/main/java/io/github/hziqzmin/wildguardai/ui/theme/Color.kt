package io.github.hziqzmin.wildguardai.ui.theme

import androidx.compose.ui.graphics.Color

// --- WILDGUARD AI v2 FIGMA PALETTE (5 CORE SECTIONS) ---
val DarkSlateGreen = Color(0xFF2D3A3A) // Section 1: Top bar, Input card, Center Nav button, Splash
val WarmCream = Color(0xFFF5F0E0)      // Section 2: Secondary background, warm surfaces
val OffWhite = Color(0xFFFAFAFA)       // Section 3: Main background, elevated cards/avatars
val NearBlack = Color(0xFF0F0F0F)      // Section 4: Primary text and high-contrast icons
val AccentTeal = Color(0xFF26A69A)     // Section 5: Interactive accents, highlights, active states

// Semantic & Alert Colors for Survival Context
val CautionAmber = Color(0xFFD97706)
val CautionBg = Color(0xFFFEF3C7)
val SosCrimson = Color(0xFFDC2626)
val MutedText = Color(0xFF9CA3AF)

// Backwards-compatible aliases used across existing screens
val DarkGreen = DarkSlateGreen
val Cream = WarmCream
val White = OffWhite
val Black = NearBlack
val AccentTurquoise = AccentTeal

// --- MATERIAL 3 LIGHT THEME MAPPING ---
val md_theme_light_primary = DarkSlateGreen
val md_theme_light_onPrimary = OffWhite
val md_theme_light_primaryContainer = DarkSlateGreen
val md_theme_light_onPrimaryContainer = OffWhite

val md_theme_light_secondary = AccentTeal
val md_theme_light_onSecondary = OffWhite
val md_theme_light_secondaryContainer = WarmCream
val md_theme_light_onSecondaryContainer = DarkSlateGreen

val md_theme_light_tertiary = AccentTeal
val md_theme_light_onTertiary = OffWhite
val md_theme_light_tertiaryContainer = WarmCream
val md_theme_light_onTertiaryContainer = NearBlack

val md_theme_light_background = OffWhite
val md_theme_light_onBackground = NearBlack

val md_theme_light_surface = OffWhite
val md_theme_light_onSurface = NearBlack
val md_theme_light_surfaceVariant = WarmCream
val md_theme_light_onSurfaceVariant = DarkSlateGreen

val md_theme_light_error = SosCrimson
val md_theme_light_onError = OffWhite
val md_theme_light_errorContainer = Color(0xFFFEE2E2)
val md_theme_light_onErrorContainer = NearBlack

val md_theme_light_outline = Color(0xFFD1D5DB)
val md_theme_light_outlineVariant = Color(0xFFE5E7EB)
val md_theme_light_inverseOnSurface = OffWhite
val md_theme_light_inverseSurface = DarkSlateGreen
val md_theme_light_inversePrimary = AccentTeal
val md_theme_light_surfaceTint = DarkSlateGreen
val md_theme_light_scrim = NearBlack