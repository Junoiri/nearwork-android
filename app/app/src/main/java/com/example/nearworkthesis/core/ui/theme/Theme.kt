package com.example.nearworkthesis.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = SmartBlue,
    onPrimary = White,
    primaryContainer = BabyBlueIceContainer,
    onPrimaryContainer = SmartBlue,
    secondary = Periwinkle,
    onSecondary = Graphite,
    secondaryContainer = PeriwinkleContainer,
    onSecondaryContainer = SmartBlue,
    tertiary = LavenderVeil,
    onTertiary = Graphite,
    tertiaryContainer = LavenderVeilContainer,
    onTertiaryContainer = SmartBlue,
    error = SmokyRose,
    onError = White,
    errorContainer = GrapefruitPinkContainer,
    onErrorContainer = SmokyRose,
    background = BrightSnow,
    onBackground = VintageGrape,
    surface = BrightSnow,
    onSurface = VintageGrape,
    surfaceVariant = BrightSnowVariant,
    onSurfaceVariant = DustyGrape,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight
)

private val DarkColorScheme = darkColorScheme(
    primary = BabyBlueIce,
    onPrimary = Graphite,
    primaryContainer = SmartBlueContainer,
    onPrimaryContainer = BabyBlueIce,
    secondary = DustyGrape,
    onSecondary = White,
    secondaryContainer = DustyGrapeContainer,
    onSecondaryContainer = LavenderVeil,
    tertiary = VintageGrape,
    onTertiary = White,
    tertiaryContainer = VintageGrapeContainer,
    onTertiaryContainer = LavenderVeil,
    error = GrapefruitPink,
    onError = Graphite,
    errorContainer = SmokyRoseContainer,
    onErrorContainer = GrapefruitPink,
    background = CarbonBlack,
    onBackground = BrightSnow,
    surface = CarbonBlack,
    onSurface = Periwinkle,
    surfaceVariant = CarbonBlackVariant,
    onSurfaceVariant = BabyBlueIce,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)
@Composable
fun NearworkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
    ) {
        Surface(
            color = Color.Transparent,
            contentColor = colorScheme.onBackground
        ) {
            content()
        }
    }
}
