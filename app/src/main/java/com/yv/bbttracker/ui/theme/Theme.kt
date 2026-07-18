package com.yv.bbttracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Rose40,
    onPrimary = Color.White,
    primaryContainer = Rose90,
    onPrimaryContainer = Ink10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Color(0xFF173630),
    tertiary = Plum40,
    onTertiary = Color.White,
    tertiaryContainer = Plum90,
    onTertiaryContainer = Color(0xFF321D2D),
    background = Rose100,
    onBackground = Ink10,
    surface = Color(0xFFFFFBFC),
    onSurface = Ink10,
    surfaceVariant = Color(0xFFF5ECEF),
    onSurfaceVariant = Color(0xFF65575D),
    outline = Color(0xFF806F75),
    outlineVariant = Color(0xFFDED0D4),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Rose80,
    onPrimary = Color(0xFF53142F),
    primaryContainer = Color(0xFF6E2943),
    onPrimaryContainer = Rose90,
    secondary = Teal80,
    secondaryContainer = Color(0xFF2D4F49),
    onSecondaryContainer = Teal90,
    tertiary = Plum80,
    tertiaryContainer = Color(0xFF563D50),
    onTertiaryContainer = Plum90,
    background = DarkSurface,
    onBackground = Ink90,
    surface = Color(0xFF23171C),
    onSurface = Ink90,
    surfaceVariant = DarkSurfaceVariant,
    outlineVariant = Color(0xFF6D5A62),
)

private val BbtShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
)

@Composable
fun BbtTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = BbtTypography,
        shapes = BbtShapes,
        content = content,
    )
}
