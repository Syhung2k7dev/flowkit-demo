package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val FlowKitColorScheme = darkColorScheme(
    primary = CyanPulse,
    onPrimary = Color(0xFF04222B),
    primaryContainer = CyanContainer,
    onPrimaryContainer = OnCyanContainer,
    secondary = AmberGovernance,
    onSecondary = Color(0xFF281700),
    secondaryContainer = AmberContainer,
    onSecondaryContainer = OnAmberContainer,
    tertiary = VioletGenerative,
    onTertiary = Color(0xFF1E1042),
    tertiaryContainer = VioletContainer,
    onTertiaryContainer = OnVioletContainer,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF1D283A),
    error = RoseDeny,
    onError = Color.White,
    errorContainer = RoseContainer,
    onErrorContainer = Color(0xFFFFD9DF),
)

private val FlowKitShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun FlowKitTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FlowKitColorScheme,
        typography = FlowKitTypography,
        shapes = FlowKitShapes,
        content = content
    )
}
