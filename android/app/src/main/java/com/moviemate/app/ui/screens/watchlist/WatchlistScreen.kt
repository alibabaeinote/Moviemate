package com.moviemate.app.ui.screens.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviemate.app.data.repository.DeckFilm
import com.moviemate.app.data.session.Session
import com.moviemate.app.di.LocalAppGraph
import com.moviemate.app.ui.components.FilmPoster
import com.moviemate.app.ui.components.PrimaryCta
import com.moviemate.app.ui.components.SecondaryCta
import com.moviemate.app.ui.components.SharedTasteAxis
import com.moviemate.app.ui.core.ActionState
import com.moviemate.app.ui.core.UiState
import com.moviemate.app.ui.core.UiStateHost
import com.moviemate.app.ui.core.factoryOf
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Radius
import com.moviemate.app.ui.theme.Space

/**
 * The shared list, split three ways.
 *
 * An unanswered item is shown as a task rather than a passive row (PRD §7.4
 * item 1), which is why "Waiting on you" carries its action inline: the whole
 * point is that the other person can answer without leaving the list.
 */
@Composable
fun WatchlistScreen() {
    val graph = LocalAppGraph.current
    val viewModel: WatchlistViewModel = viewModel(
        factory = factoryOf {
            WatchlistViewModel(graph.pairRepository, graph.filmRepository, graph.sessionStore)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    val session by viewModel.sessionFlow.collectAsStateWithLifecycle()
    val colors = MovieMateTheme.colors

    val activeSearch = search
    if (activeSearch != null) {
        AddFilmSheet(
            state = activeSearch,
            busy = action.isRunning,
            onQueryChange = viewModel::searchFilms,
            onAdd = viewModel::addFilm,
            onClose = viewModel::closeSearch,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceGround)
            .padding(horizontal = Space.screenGutter),
    ) {
        Spacer(Modifier.height(Space.screenTop))
        Text("YOUR LIST", style = MovieMateType.megaHeadline, color = colors.textPrimary)
        Spacer(Modifier.height(Space.stackTight))
        PrimaryCta(label = "Add a film", onClick = viewModel::openSearch)
        Spacer(Modifier.height(Space.stack))

        (action as? ActionState.Failed)?.let {
            Text(it.message, style = MovieMateType.meta, color = colors.statusDecorative)
            Spacer(Modifier.height(Space.stackTight))
        }

        UiStateHost(state = state) { sections ->
            val current = session
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.stackTight)) {
                sections.forEach { (section, rows) ->
                    item(key = "header-${section.name}") {
                        Text(
                            text = section.title.uppercase(),
                            style = MovieMateType.overline,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = Space.stackTight),
                        )
                    }
                    items(rows, key = { it.id }) { row ->
                        WatchlistRowCard(
                            row = row,
                            session = current,
                            busy = action.isRunning,
                            onCommit = { viewModel.commit(row.id) },
                            onRemove = { viewModel.remove(row.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistRowCard(
    row: WatchlistRow,
    session: Session?,
    busy: Boolean,
    onCommit: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MovieMateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(colors.surfaceRaised)
            .padding(Space.stackTight),
        verticalArrangement = Arrangement.spacedBy(Space.stackTight),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.stackTight)) {
            Box(Modifier.width(POSTER_THUMB_WIDTH)) {
                FilmPoster(
                    posterPath = row.film?.posterPath,
                    title = row.film?.title ?: row.item.filmId,
                    cornerRadius = Radius.chip,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Space.inlineTight),
            ) {
                Text(
                    text = row.film?.title ?: "Film ${row.item.filmId}",
                    style = MovieMateType.statCaption,
                    color = colors.textPrimary,
                )
                if (session != null) {
                    // Who put this here — partner activity, visible (§7.4 item 2).
                    Text(
                        text = row.attribution(session),
                        style = MovieMateType.meta,
                        color = colors.textSecondary,
                    )
                }
                val shared = row.item.mutualScore
                if (row.section == WatchlistSection.Watched && shared != null) {
                    Text(
                        text = "${shared.toInt()} together",
                        style = MovieMateType.meta,
                        color = colors.textAccent,
                    )
                }
            }
        }

        // Two markers on one axis, never two separate numbers: the shared taste
        // is the product, so it gets one line (§7.4 item 6).
        if (row.section == WatchlistSection.Watched && row.scores.size == 2) {
            val (first, second) = row.scores.values.toList()
            SharedTasteAxis(scoreA = first.toFloat(), scoreB = second.toFloat())
        }

        when (row.section) {
            WatchlistSection.WaitingOnYou -> PrimaryCta(
                label = "I'm in too",
                onClick = onCommit,
                enabled = !busy,
            )

            WatchlistSection.WaitingOnThem -> Text(
                text = "You're in. Waiting on your partner.",
                style = MovieMateType.meta,
                color = colors.statusPending,
            )

            WatchlistSection.Ready -> Text(
                text = "You're both in.",
                style = MovieMateType.meta,
                color = colors.textReward,
            )

            WatchlistSection.Watched -> Unit
        }

        SecondaryCta(label = "Remove", onClick = onRemove)
    }
}

@Composable
private fun AddFilmSheet(
    state: UiState<List<DeckFilm>>,
    busy: Boolean,
    onQueryChange: (String) -> Unit,
    onAdd: (DeckFilm) -> Unit,
    onClose: () -> Unit,
) {
    val colors = MovieMateTheme.colors
    var query by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceGround)
            .padding(horizontal = Space.screenGutter),
    ) {
        Spacer(Modifier.height(Space.screenTop))
        Text("ADD A FILM", style = MovieMateType.megaHeadline, color = colors.textPrimary)
        Spacer(Modifier.height(Space.stackTight))

        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onQueryChange(it)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.chip),
            textStyle = MovieMateType.body,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.textAccent,
                unfocusedBorderColor = colors.borderHairline,
                focusedContainerColor = colors.surfaceRaised,
                unfocusedContainerColor = colors.surfaceRaised,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                cursorColor = colors.textAccent,
            ),
        )

        Spacer(Modifier.height(Space.stack))

        Box(Modifier.weight(1f)) {
            UiStateHost(state = state) { films ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.stackTight)) {
                    items(films, key = { it.filmId }) { film ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.card))
                                .background(colors.surfaceRaised)
                                .padding(Space.stackTight),
                            horizontalArrangement = Arrangement.spacedBy(Space.stackTight),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(POSTER_THUMB_WIDTH)) {
                                FilmPoster(
                                    posterPath = film.posterPath,
                                    title = film.title,
                                    cornerRadius = Radius.chip,
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(Space.inlineTight),
                            ) {
                                Text(
                                    text = film.title,
                                    style = MovieMateType.statCaption,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = film.releaseYear.takeIf { it > 0 }?.toString().orEmpty(),
                                    style = MovieMateType.meta,
                                    color = colors.textSecondary,
                                )
                                PrimaryCta(
                                    label = "Tell your partner",
                                    onClick = { onAdd(film) },
                                    enabled = !busy,
                                )
                            }
                        }
                    }
                }
            }
        }

        SecondaryCta(label = "Cancel", onClick = onClose)
        Spacer(Modifier.height(Space.stack))
    }
}

private val POSTER_THUMB_WIDTH = 72.dp
