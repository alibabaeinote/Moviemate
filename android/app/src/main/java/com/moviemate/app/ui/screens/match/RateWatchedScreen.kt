package com.moviemate.app.ui.screens.match

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviemate.app.di.LocalAppGraph
import com.moviemate.app.ui.components.FilmPoster
import com.moviemate.app.ui.components.PrimaryCta
import com.moviemate.app.ui.components.SecondaryCta
import com.moviemate.app.ui.components.TasteDialSlider
import com.moviemate.app.ui.core.ActionState
import com.moviemate.app.ui.core.UiStateHost
import com.moviemate.app.ui.core.factoryOf
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Space

/**
 * The Taste Dial after a watch — where the loop feeds back into the recommender.
 *
 * Each partner rates separately. A single shared score would average away the
 * disagreement, and the gap between two scores is exactly what the mutual score
 * is built from.
 */
@Composable
fun RateWatchedScreen(
    matchId: String,
    onDone: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val viewModel: RateWatchedViewModel = viewModel(
        factory = factoryOf {
            RateWatchedViewModel(
                graph.pairRepository,
                graph.filmRepository,
                graph.sessionStore,
                matchId,
            )
        },
    )
    val filmState by viewModel.film.collectAsStateWithLifecycle()
    val score by viewModel.score.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    val colors = MovieMateTheme.colors

    LaunchedEffect(action) { if (action is ActionState.Succeeded) onDone() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceGround)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screenGutter, vertical = Space.screenTop),
        verticalArrangement = Arrangement.spacedBy(Space.stack),
    ) {
        Text("HOW WAS IT?", style = MovieMateType.megaHeadline, color = colors.textPrimary)

        UiStateHost(state = filmState) { film ->
            Column(verticalArrangement = Arrangement.spacedBy(Space.stack)) {
                if (film != null) {
                    FilmPoster(posterPath = film.posterPath, title = film.title)
                    Text(film.title, style = MovieMateType.filmTitle, color = colors.textPrimary)
                }

                Spacer(Modifier.height(Space.stackTight))

                TasteDialSlider(score = score, onScoreChange = viewModel::setScore)

                Spacer(Modifier.height(Space.stackTight))

                PrimaryCta(
                    label = if (action.isRunning) "Saving…" else "Save my score",
                    onClick = viewModel::submit,
                    enabled = !action.isRunning,
                )

                (action as? ActionState.Failed)?.let {
                    Text(it.message, style = MovieMateType.meta, color = colors.statusDecorative)
                }

                SecondaryCta(label = "Later", onClick = onDone)
            }
        }
    }
}
