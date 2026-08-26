package com.moviemate.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * MovieMate theme.
 *
 * Light only, portrait only — the design system defines a single palette, and
 * a dark variant would need its own token set rather than an automatic invert
 * (PRD §12 leaves orientation open; portrait is the current default).
 */
private val MovieMateColorScheme = lightColorScheme(
    primary = MovieMateColors.Blue,
    onPrimary = MovieMateColors.OnBlue,
    primaryContainer = MovieMateColors.BlueSoft,
    onPrimaryContainer = MovieMateColors.Blue,
    secondary = MovieMateColors.Lime,
    onSecondary = MovieMateColors.OnLime,
    background = MovieMateColors.Background,
    onBackground = MovieMateColors.Ink,
    surface = MovieMateColors.Paper,
    onSurface = MovieMateColors.Ink,
    onSurfaceVariant = MovieMateColors.InkSecondary,
    error = MovieMateColors.Coral,
)

@Composable
fun MovieMateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MovieMateColorScheme,
        typography = MovieMateTypography,
        content = content,
    )
}
