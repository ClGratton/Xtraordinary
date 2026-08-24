package com.xteink.companion.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.xteink.companion.ui.CompanionVisualTheme
import com.xteink.companion.ui.CompanionColorMode

private val ExpressiveColors = lightColorScheme(
    primary = ExpressivePrimary,
    onPrimary = ExpressiveOnPrimary,
    primaryContainer = ExpressivePrimaryContainer,
    onPrimaryContainer = ExpressiveOnPrimaryContainer,
    secondary = ExpressiveSecondary,
    onSecondary = ExpressiveOnSecondary,
    secondaryContainer = ExpressiveSecondaryContainer,
    onSecondaryContainer = ExpressiveOnSecondaryContainer,
    background = ExpressiveBackground,
    onBackground = ExpressiveOnBackground,
    surface = ExpressiveSurface,
    onSurface = ExpressiveOnBackground,
    surfaceContainer = ExpressiveSurfaceContainer,
    surfaceContainerLow = ExpressiveSurfaceLow,
    surfaceVariant = ExpressiveSurfaceVariant,
    onSurfaceVariant = Color(0xFF5D4540),
    outline = ExpressiveOutline,
    tertiary = ExpressiveTertiary,
    onTertiary = ExpressiveOnTertiary,
    tertiaryContainer = ExpressiveTertiaryContainer,
    onTertiaryContainer = ExpressiveOnTertiaryContainer,
    error = ExpressiveError,
    onError = ExpressiveOnError,
    errorContainer = ExpressiveErrorContainer,
    onErrorContainer = ExpressiveOnErrorContainer,
)

private val QuietColors = darkColorScheme(
    primary = QuietPrimary,
    onPrimary = QuietOnPrimary,
    primaryContainer = QuietPrimaryContainer,
    onPrimaryContainer = QuietOnPrimaryContainer,
    secondary = QuietSecondary,
    onSecondary = QuietOnSecondary,
    secondaryContainer = QuietSecondaryContainer,
    onSecondaryContainer = QuietOnSecondaryContainer,
    background = QuietBackground,
    onBackground = QuietOnBackground,
    surface = QuietSurface,
    onSurface = QuietOnBackground,
    surfaceContainer = QuietSurfaceContainer,
    surfaceContainerLow = QuietSurfaceLow,
    surfaceVariant = QuietSurfaceVariant,
    onSurfaceVariant = Color(0xFFC8C8C8),
    outline = QuietOutline,
    tertiary = QuietTertiary,
    onTertiary = QuietOnTertiary,
    tertiaryContainer = QuietTertiaryContainer,
    onTertiaryContainer = QuietOnTertiaryContainer,
    error = QuietError,
    onError = QuietOnError,
    errorContainer = QuietErrorContainer,
    onErrorContainer = QuietOnErrorContainer,
)

private val CompanionShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(36.dp),
    extraLarge = RoundedCornerShape(48.dp),
)

private val MinimalShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

val LocalCompanionVisualTheme = staticCompositionLocalOf { CompanionVisualTheme.Expressive }

private val CompanionTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 68.sp,
        lineHeight = 72.sp,
        letterSpacing = (-1.5).sp,
        fontFeatureSettings = "tnum",
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1).sp,
        fontFeatureSettings = "tnum",
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 31.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
)

@Composable
fun X3CompanionTheme(
    visualTheme: CompanionVisualTheme = CompanionVisualTheme.Expressive,
    colorMode: CompanionColorMode = CompanionColorMode.Light,
    colorModeProgress: Float = colorMode.ordinal.toFloat(),
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dynamicColorAvailable = useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val lightScheme = if (dynamicColorAvailable) dynamicLightColorScheme(context) else ExpressiveColors
    val darkScheme = if (dynamicColorAvailable) dynamicDarkColorScheme(context) else QuietColors
    val colorScheme = interpolateColorScheme(lightScheme, darkScheme, colorModeProgress)

    CompositionLocalProvider(LocalCompanionVisualTheme provides visualTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = if (visualTheme == CompanionVisualTheme.Minimal) MinimalShapes else CompanionShapes,
            typography = CompanionTypography,
            content = content,
        )
    }
}

/** Keeps the app palette in lockstep with the focused artwork pager. */
internal fun interpolateColorScheme(light: ColorScheme, dark: ColorScheme, progress: Float): ColorScheme {
    val p = progress.coerceIn(0f, 1f)
    fun role(from: Color, to: Color) = lerp(from, to, p)
    fun on(background: Color) = if (background.luminance() > 0.18f) Color.Black else Color.White
    val primary = role(light.primary, dark.primary)
    val primaryContainer = role(light.primaryContainer, dark.primaryContainer)
    val secondary = role(light.secondary, dark.secondary)
    val secondaryContainer = role(light.secondaryContainer, dark.secondaryContainer)
    val tertiary = role(light.tertiary, dark.tertiary)
    val tertiaryContainer = role(light.tertiaryContainer, dark.tertiaryContainer)
    val background = role(light.background, dark.background)
    val surface = role(light.surface, dark.surface)
    val surfaceVariant = role(light.surfaceVariant, dark.surfaceVariant)
    val error = role(light.error, dark.error)
    return light.copy(
        primary = primary, onPrimary = on(primary),
        primaryContainer = primaryContainer, onPrimaryContainer = on(primaryContainer),
        secondary = secondary, onSecondary = on(secondary),
        secondaryContainer = secondaryContainer, onSecondaryContainer = on(secondaryContainer),
        tertiary = tertiary, onTertiary = on(tertiary),
        tertiaryContainer = tertiaryContainer, onTertiaryContainer = on(tertiaryContainer),
        background = background, onBackground = on(background),
        surface = surface, onSurface = on(surface),
        surfaceVariant = surfaceVariant, onSurfaceVariant = on(surfaceVariant),
        error = error, onError = on(error),
        errorContainer = role(light.errorContainer, dark.errorContainer),
        onErrorContainer = on(role(light.errorContainer, dark.errorContainer)),
        outline = role(light.outline, dark.outline),
        outlineVariant = role(light.outlineVariant, dark.outlineVariant),
        surfaceContainerLowest = role(light.surfaceContainerLowest, dark.surfaceContainerLowest),
        surfaceContainerLow = role(light.surfaceContainerLow, dark.surfaceContainerLow),
        surfaceContainer = role(light.surfaceContainer, dark.surfaceContainer),
        surfaceContainerHigh = role(light.surfaceContainerHigh, dark.surfaceContainerHigh),
        surfaceContainerHighest = role(light.surfaceContainerHighest, dark.surfaceContainerHighest),
    )
}
