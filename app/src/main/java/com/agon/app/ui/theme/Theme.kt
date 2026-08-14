package com.agon.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Color(0xFF06222F),
    secondary = GreenAccent,
    onSecondary = Color(0xFF06231B),
    tertiary = AmberAccent,
    onTertiary = Color(0xFF2B1F05),
    background = DeepNavy,
    onBackground = TextPrimary,
    surface = DeepNavy,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceNavy,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = SurfaceNavy,
    surfaceContainerHigh = SurfaceNavy2,
    surfaceContainerHighest = SurfaceNavy2,
    error = DangerRed,
    errorContainer = DangerContainer,
    onErrorContainer = Color(0xFFF2B8AF),
    outline = OutlineColor,
)

/**
 * Arabic-safe text style: keep font padding and never trim line heights so
 * tall Arabic glyphs (ألف المد، التشكيل، اللام ألف) are never clipped
 * in the middle when a Text overrides fontSize without a lineHeight.
 */
private fun TextStyle.arabicSafe(): TextStyle = copy(
    platformStyle = PlatformTextStyle(includeFontPadding = true),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

private val Base = Typography()
private val AppTypography = Typography(
    displayLarge = Base.displayLarge.arabicSafe(),
    displayMedium = Base.displayMedium.arabicSafe(),
    displaySmall = Base.displaySmall.arabicSafe(),
    headlineLarge = Base.headlineLarge.arabicSafe(),
    headlineMedium = Base.headlineMedium.arabicSafe(),
    headlineSmall = Base.headlineSmall.arabicSafe(),
    titleLarge = Base.titleLarge.arabicSafe(),
    titleMedium = Base.titleMedium.arabicSafe(),
    titleSmall = Base.titleSmall.arabicSafe(),
    bodyLarge = Base.bodyLarge.arabicSafe(),
    bodyMedium = Base.bodyMedium.arabicSafe(),
    bodySmall = Base.bodySmall.arabicSafe(),
    labelLarge = Base.labelLarge.arabicSafe(),
    labelMedium = Base.labelMedium.arabicSafe(),
    labelSmall = Base.labelSmall.arabicSafe(),
)

@Composable
fun AgonAppTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // The product design is a dedicated dark experience (see reference mockups),
    // so the dark scheme is always applied for visual consistency.
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content,
    )
}
