package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ShredForgeDarkColorScheme = darkColorScheme(
    primary = TubeAmber,
    onPrimary = ObsidianBg,
    primaryContainer = TubeAmberContainer,
    onPrimaryContainer = TubeAmberGlow,
    secondary = VoltCyan,
    onSecondary = ObsidianBg,
    secondaryContainer = VoltCyanContainer,
    onSecondaryContainer = VoltCyan,
    tertiary = CrimsonLed,
    onTertiary = TextPrimary,
    tertiaryContainer = CrimsonLedContainer,
    onTertiaryContainer = TextPrimary,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ChassisSurface,
    onSurface = TextPrimary,
    surfaceVariant = ModuleSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ChassisSurface,
    surfaceContainer = ModuleSurface,
    surfaceContainerHigh = ModuleElevated,
    surfaceContainerHighest = BezelBorder,
    outline = BezelBorder,
    outlineVariant = ColorTokens.SubtleBorder,
    error = CrimsonLed,
    onError = TextPrimary,
    errorContainer = CrimsonLedContainer,
    onErrorContainer = TextPrimary,
)

private object ColorTokens {
    val SubtleBorder = androidx.compose.ui.graphics.Color(0xFF232732)
}

val ShredForgeShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun ShredForgeTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ShredForgeDarkColorScheme,
        typography = ShredForgeTypography,
        shapes = ShredForgeShapes,
        content = content,
    )
}
