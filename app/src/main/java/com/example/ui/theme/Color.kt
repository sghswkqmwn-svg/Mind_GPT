package com.example.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Minecraft-Inspired Emerald, Diamond & Obsidian Palette
val EmeraldGlow = Color(0xFF10B981)
val EmeraldDeep = Color(0xFF059669)
val EmeraldBright = Color(0xFF34D399)
val EmeraldContainerDark = Color(0xFF0B3B2B)
val EmeraldContainerLight = Color(0xFFD1FAE5)

val DiamondCyan = Color(0xFF22D3EE)
val DiamondDeep = Color(0xFF0891B2)
val DiamondContainerDark = Color(0xFF0C3642)
val DiamondContainerLight = Color(0xFFCFFAFE)

val GoldAmber = Color(0xFFF59E0B)
val GoldContainerDark = Color(0xFF3D2807)
val GoldContainerLight = Color(0xFFFEF3C7)

val RedstoneRed = Color(0xFFEF4444)
val RedstoneDark = Color(0xFFDC2626)
val RedstoneContainerDark = Color(0xFF450A0A)
val RedstoneContainerLight = Color(0xFFFEE2E2)

// Obsidian / Deepslate Dark Surfaces
val ObsidianBg = Color(0xFF09120D)
val DeepslateSurface = Color(0xFF101C16)
val DeepslateContainer = Color(0xFF16261E)
val DeepslateContainerHigh = Color(0xFF1E3228)
val DeepslateOutline = Color(0xFF2E4A3B)
val DeepslateTextPrimary = Color(0xFFECFDF5)
val DeepslateTextSecondary = Color(0xFF9CAFA5)

// Daylight Overworld Light Surfaces
val OverworldBg = Color(0xFFF3F8F4)
val OverworldSurface = Color(0xFFFFFFFF)
val OverworldContainer = Color(0xFFE7F2EB)
val OverworldContainerHigh = Color(0xFFD9EAE0)
val OverworldOutline = Color(0xFFB7D1C2)
val OverworldTextPrimary = Color(0xFF0C1D14)
val OverworldTextSecondary = Color(0xFF446152)

val MindGptDarkColorScheme = darkColorScheme(
    primary = EmeraldGlow,
    onPrimary = Color(0xFF022015),
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = EmeraldBright,
    secondary = DiamondCyan,
    onSecondary = Color(0xFF04242B),
    secondaryContainer = DiamondContainerDark,
    onSecondaryContainer = Color(0xFFA5F3FC),
    tertiary = GoldAmber,
    onTertiary = Color(0xFF2B1900),
    tertiaryContainer = GoldContainerDark,
    onTertiaryContainer = Color(0xFFFDE68A),
    error = RedstoneRed,
    onError = Color.White,
    errorContainer = RedstoneContainerDark,
    onErrorContainer = Color(0xFFFECACA),
    background = ObsidianBg,
    onBackground = DeepslateTextPrimary,
    surface = DeepslateSurface,
    onSurface = DeepslateTextPrimary,
    surfaceVariant = DeepslateContainer,
    onSurfaceVariant = DeepslateTextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = Color(0xFF0E1813),
    surfaceContainer = DeepslateContainer,
    surfaceContainerHigh = DeepslateContainerHigh,
    surfaceContainerHighest = Color(0xFF263E32),
    outline = DeepslateOutline,
    outlineVariant = Color(0xFF1F352A),
)

val MindGptLightColorScheme = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = DiamondDeep,
    onSecondary = Color.White,
    secondaryContainer = DiamondContainerLight,
    onSecondaryContainer = Color(0xFF164E63),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    tertiaryContainer = GoldContainerLight,
    onTertiaryContainer = Color(0xFF78350F),
    error = RedstoneDark,
    onError = Color.White,
    errorContainer = RedstoneContainerLight,
    onErrorContainer = Color(0xFF7F1D1D),
    background = OverworldBg,
    onBackground = OverworldTextPrimary,
    surface = OverworldSurface,
    onSurface = OverworldTextPrimary,
    surfaceVariant = OverworldContainer,
    onSurfaceVariant = OverworldTextSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FCF9),
    surfaceContainer = OverworldContainer,
    surfaceContainerHigh = OverworldContainerHigh,
    surfaceContainerHighest = Color(0xFFCDE2D5),
    outline = OverworldOutline,
    outlineVariant = Color(0xFFD2E4D9),
)
