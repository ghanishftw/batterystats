package com.gnsh.batterystats.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback palette when dynamic color is off: a fresh "charged" green.
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9CD67D),
    onPrimary = Color(0xFF0E3900),
    primaryContainer = Color(0xFF235107),
    onPrimaryContainer = Color(0xFFB7F397),
    secondary = Color(0xFFBCCBAF),
    onSecondary = Color(0xFF273421),
    secondaryContainer = Color(0xFF3D4B36),
    onSecondaryContainer = Color(0xFFD8E7CA),
    tertiary = Color(0xFFA0CFD0),
    onTertiary = Color(0xFF003738),
    tertiaryContainer = Color(0xFF1E4E4F),
    onTertiaryContainer = Color(0xFFBBEBEC),
    background = Color(0xFF11140F),
    surface = Color(0xFF11140F),
    surfaceContainerLowest = Color(0xFF0C0F0A),
    surfaceContainerLow = Color(0xFF191D17),
    surfaceContainer = Color(0xFF1D211B),
    surfaceContainerHigh = Color(0xFF282B25),
    surfaceContainerHighest = Color(0xFF33362F),
)

private val LightScheme = expressiveLightColorScheme().copy(
    primary = Color(0xFF3A6A1F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F397),
    onPrimaryContainer = Color(0xFF0A2100),
    secondary = Color(0xFF55624C),
    secondaryContainer = Color(0xFFD8E7CA),
    onSecondaryContainer = Color(0xFF131F0D),
    tertiary = Color(0xFF386667),
    tertiaryContainer = Color(0xFFBBEBEC),
    onTertiaryContainer = Color(0xFF002020),
    background = Color(0xFFF8FBF0),
    surface = Color(0xFFF8FBF0),
    surfaceContainerLow = Color(0xFFF2F5EA),
    surfaceContainer = Color(0xFFECEFE4),
    surfaceContainerHigh = Color(0xFFE7E9DF),
    surfaceContainerHighest = Color(0xFFE1E4D9),
)

@Composable
fun BatteryStatsTheme(dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && dark -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(),
        typography = Typography(),
        content = content,
    )
}
