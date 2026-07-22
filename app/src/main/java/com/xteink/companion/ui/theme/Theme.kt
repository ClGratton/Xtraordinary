package com.xteink.companion.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.xteink.companion.ui.CompanionVisualTheme

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
)

private val CompanionShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(36.dp),
    extraLarge = RoundedCornerShape(48.dp),
)

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
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dynamicColorAvailable = useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamicColorAvailable && visualTheme == CompanionVisualTheme.Expressive -> dynamicLightColorScheme(context)
        visualTheme == CompanionVisualTheme.Expressive -> ExpressiveColors
        else -> QuietColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = CompanionShapes,
        typography = CompanionTypography,
        content = content,
    )
}
