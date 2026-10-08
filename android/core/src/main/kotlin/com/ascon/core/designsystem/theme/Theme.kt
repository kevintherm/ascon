package com.ascon.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Material 3 is plumbing only. Every slot is overridden with Ascon tokens so a stock
 * Material component never shows the default purple look.
 */
private val Colors = lightColorScheme(
    primary = AsconColors.Accent,
    onPrimary = AsconColors.Surface,
    primaryContainer = AsconColors.Ink,
    onPrimaryContainer = AsconColors.Surface,
    inversePrimary = AsconColors.Accent2,
    secondary = AsconColors.Ink,
    onSecondary = AsconColors.Surface,
    secondaryContainer = AsconColors.SurfaceMuted,
    onSecondaryContainer = AsconColors.Ink,
    tertiary = AsconColors.Ink2,
    onTertiary = AsconColors.Surface,
    tertiaryContainer = AsconColors.SurfaceMuted,
    onTertiaryContainer = AsconColors.Ink,
    background = AsconColors.Ground,
    onBackground = AsconColors.Ink,
    surface = AsconColors.Surface,
    onSurface = AsconColors.Ink,
    surfaceVariant = AsconColors.SurfaceMuted,
    onSurfaceVariant = AsconColors.TextMuted,
    surfaceTint = AsconColors.Surface,
    inverseSurface = AsconColors.Ink,
    inverseOnSurface = AsconColors.Surface,
    error = AsconColors.Accent,
    onError = AsconColors.Surface,
    errorContainer = AsconColors.SurfaceMuted,
    onErrorContainer = AsconColors.Ink,
    outline = AsconColors.Border,
    outlineVariant = AsconColors.SurfaceSunken,
    scrim = AsconColors.Scrim,
    surfaceBright = AsconColors.Surface,
    surfaceDim = AsconColors.SurfaceSunken,
    surfaceContainerLowest = AsconColors.Surface,
    surfaceContainerLow = AsconColors.Surface,
    surfaceContainer = AsconColors.Surface,
    surfaceContainerHigh = AsconColors.Surface,
    surfaceContainerHighest = AsconColors.SurfaceMuted
)

private val Type = Typography(
    displayLarge = AsconType.Display,
    displayMedium = AsconType.Display,
    displaySmall = AsconType.ScreenTitle,
    headlineLarge = AsconType.ScreenTitle,
    headlineMedium = AsconType.HeaderTitle,
    headlineSmall = AsconType.HeaderTitleSmall,
    titleLarge = AsconType.SectionTitle,
    titleMedium = AsconType.RowTitle,
    titleSmall = AsconType.MetaStrong,
    bodyLarge = AsconType.Body,
    bodyMedium = AsconType.Body,
    bodySmall = AsconType.Meta,
    labelLarge = AsconType.Button,
    labelMedium = AsconType.CaptionStrong,
    labelSmall = AsconType.Caption
)

private val ShapeScale = Shapes(
    extraSmall = RoundedCornerShape(AsconRadius.CoverThumb),
    small = RoundedCornerShape(AsconRadius.Chip),
    medium = RoundedCornerShape(AsconRadius.Tile),
    large = RoundedCornerShape(AsconRadius.Card),
    extraLarge = RoundedCornerShape(AsconRadius.Sheet)
)

@Composable
fun AsconTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, shapes = ShapeScale) {
        CompositionLocalProvider(
            LocalContentColor provides AsconColors.Ink,
            LocalTextStyle provides AsconType.Body,
            content = content
        )
    }
}
