package com.moviemate.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Space

/**
 * Stand-in for a screen that is routed but not yet built.
 *
 * Deliberately plain: it should look unfinished, so nobody mistakes it for a
 * completed screen from the design system.
 */
@Composable
fun PlaceholderScreen(title: String, note: String) {
    val colors = MovieMateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceGround)
            .padding(Space.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Space.stackTight),
    ) {
        Text(
            text = title.uppercase(),
            style = MovieMateType.megaHeadline,
            color = colors.textPrimary,
        )
        Text(text = note, style = MovieMateType.body, color = colors.textPrimary)
        Text(text = "Not built yet.", style = MovieMateType.meta, color = colors.textSecondary)
    }
}
