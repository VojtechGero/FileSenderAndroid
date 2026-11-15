package com.example.filesender.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Day = lightColorScheme(
    primary = Fern, onPrimary = Color.White, primaryContainer = Color(0xFFDFEFDF),
    onPrimaryContainer = Pine, secondary = Color(0xFF456252),
    secondaryContainer = Color(0xFFDFEFDF), onSecondaryContainer = Pine,
    background = Paper, onBackground = Ink, surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFEBF1E8), onSurfaceVariant = Color(0xFF50665F),
    outline = Color(0xFF71837B), outlineVariant = Color(0xFFD4DED8)
)
private val Night = darkColorScheme(
    primary = Sprout, onPrimary = Pine, primaryContainer = Color(0xFF284B3C),
    onPrimaryContainer = Color(0xFFE0F3D7), secondary = Color(0xFFB4CDC0),
    secondaryContainer = Color(0xFF29483B), onSecondaryContainer = Sprout,
    background = Color(0xFF101C17), onBackground = Color(0xFFEDF3E9),
    surface = Color(0xFF1A2B23), onSurface = Color(0xFFEDF3E9),
    surfaceVariant = Color(0xFF24392F), onSurfaceVariant = Color(0xFFB9CABE),
    outline = Color(0xFF90A398), outlineVariant = Color(0xFF3B5044)
)

@Composable
fun FileSenderTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Night else Day, typography = Typography, content = content)
}
