package com.moviemate.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import com.moviemate.app.data.remote.TmdbApi
import com.moviemate.app.ui.theme.Layout
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Radius
import com.moviemate.app.ui.theme.Space

/**
 * A film poster at the design system's hero ratio.
 *
 * Falls back to the title set on the sunken surface rather than a broken-image
 * glyph: TMDB genuinely has no poster for some titles, and that is a normal
 * result, not an error worth showing someone.
 */
@Composable
fun FilmPoster(
    posterPath: String?,
    title: String,
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = Radius.hero,
) {
    val colors = MovieMateTheme.colors
    val url = TmdbApi.posterUrl(posterPath)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(Layout.POSTER_HERO_RATIO)
            .clip(RoundedCornerShape(cornerRadius))
            .background(colors.surfaceSunken),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = "Poster for $title",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = title,
                style = MovieMateType.filmTitle,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(Space.stack),
            )
        }
    }
}
