package com.moviemate.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.MovieMateType

/**
 * A person's picture, or their initial on a flat tint when they have none.
 *
 * [ringColor] is `colors.partnerA` / `colors.partnerB` at every call site —
 * one color is always one person, on every screen (Design System §4.5), and
 * the avatar ring is where that identity shows up outside the Taste Dial.
 */
@Composable
fun Avatar(
    name: String?,
    avatarUrl: String?,
    ringColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val colors = MovieMateTheme.colors

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(BorderStroke(2.dp, ringColor), CircleShape)
            .background(colors.surfaceSunken),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = if (name != null) "$name's picture" else "Profile picture",
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            Text(
                text = name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MovieMateType.statCaption,
                color = colors.textSecondary,
            )
        }
    }
}
