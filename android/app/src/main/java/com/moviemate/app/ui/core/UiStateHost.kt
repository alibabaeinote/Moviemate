package com.moviemate.app.ui.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.moviemate.app.ui.components.SecondaryCta
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Space

/**
 * Renders the four [UiState] cases so every screen's loading, empty and error
 * moments look like the same product.
 *
 * Screens pass copy, not layout. That is the whole point: a screen deciding for
 * itself what an error looks like is how an app ends up with four different
 * error treatments, none of them designed.
 */
@Composable
fun <T> UiStateHost(
    state: UiState<T>,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        is UiState.Loading -> CenteredMessage(modifier) {
            CircularProgressIndicator(color = MovieMateTheme.colors.textAccent)
        }

        is UiState.Empty -> CenteredMessage(modifier) {
            Text(
                text = state.headline,
                style = MovieMateType.statCaption,
                color = MovieMateTheme.colors.textPrimary,
            )
            if (state.detail != null) {
                Text(
                    text = state.detail,
                    style = MovieMateType.body,
                    color = MovieMateTheme.colors.textSecondary,
                )
            }
        }

        is UiState.Failed -> CenteredMessage(modifier) {
            Text(
                text = state.message,
                style = MovieMateType.body,
                color = MovieMateTheme.colors.textPrimary,
            )
            // Offering a retry for something that cannot succeed on a second
            // attempt — an expired invite code — teaches people the button lies.
            if (state.retryable && onRetry != null) {
                SecondaryCta(label = "Try again", onClick = onRetry)
            }
        }

        is UiState.Content -> content(state.value)
    }
}

@Composable
private fun CenteredMessage(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(Space.screenGutter),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.stack),
        ) { content() }
    }
}
