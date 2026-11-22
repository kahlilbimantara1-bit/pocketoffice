package com.pocketoffice.lite.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF00695C),
    onPrimary = Color.White,
    background = Color(0xFFF7F9F9),
    onBackground = Color(0xFF1B1F1F),
    surface = Color.White,
    onSurface = Color(0xFF1B1F1F),
    surfaceVariant = Color(0xFFE3EAE9),
    onSurfaceVariant = Color(0xFF4A5655)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    onPrimary = Color(0xFF00201C),
    background = Color(0xFF121515),
    onBackground = Color(0xFFE2E6E5),
    surface = Color(0xFF1A1E1E),
    onSurface = Color(0xFFE2E6E5),
    surfaceVariant = Color(0xFF2A3130),
    onSurfaceVariant = Color(0xFFB4BFBD)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp)
)

@Composable
fun PocketOfficeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        content = content
    )
}
