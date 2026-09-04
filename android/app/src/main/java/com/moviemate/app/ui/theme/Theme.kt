package com.moviemate.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Theme wiring.
 *
 * Semantic roles reach components through [MovieMateTheme.colors] rather than a
 * global object, so a subtree can be re-themed (a light sheet over a dark app,
 * a preview pinned to one scheme) without any component knowing.
 *
 * Material's own scheme is populated alongside ours purely so stock M3
 * components — text fields, ripples, dialogs — inherit the right colours. Our
 * own components read [MovieMateTheme.colors], never MaterialTheme.colorScheme.
 */

private val LocalMovieMateColors = staticCompositionLocalOf<MovieMateColorScheme> {
    error("MovieMateColorScheme not provided — wrap the tree in MovieMateTheme { }.")
}

object MovieMateTheme {
    /** Semantic colour roles for the current theme. */
    val colors: MovieMateColorScheme
        @Composable @ReadOnlyComposable get() = LocalMovieMateColors.current
}

/**
 * The app is dark by default; [darkTheme] exists so previews and screenshot
 * tests can pin a scheme.
 */
@Composable
fun MovieMateTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors

    val material = if (darkTheme) {
        darkColorScheme(
            primary = colors.actionPrimaryFill,
            onPrimary = colors.textOnFill,
            primaryContainer = colors.surfaceAccent,
            onPrimaryContainer = colors.textAccent,
            secondary = colors.actionRewardFill,
            onSecondary = colors.textOnReward,
            background = colors.surfaceGround,
            onBackground = colors.textPrimary,
            surface = colors.surfaceRaised,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceSunken,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.borderHairline,
            error = colors.statusDecorative,
        )
    } else {
        lightColorScheme(
            primary = colors.actionPrimaryFill,
            onPrimary = colors.textOnFill,
            primaryContainer = colors.surfaceAccent,
            onPrimaryContainer = colors.textAccent,
            secondary = colors.actionRewardFill,
            onSecondary = colors.textOnReward,
            background = colors.surfaceGround,
            onBackground = colors.textPrimary,
            surface = colors.surfaceRaised,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceSunken,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.borderHairline,
            error = colors.statusDecorative,
        )
    }

    CompositionLocalProvider(LocalMovieMateColors provides colors) {
        MaterialTheme(
            colorScheme = material,
            typography = MovieMateTypography,
            content = content,
        )
    }
}

/** Follows the system setting. Not used by the app shell, which pins dark. */
@Composable
fun MovieMateThemeAuto(content: @Composable () -> Unit) =
    MovieMateTheme(darkTheme = isSystemInDarkTheme(), content = content)
