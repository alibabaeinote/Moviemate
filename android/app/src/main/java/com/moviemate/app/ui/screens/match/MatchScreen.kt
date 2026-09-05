package com.moviemate.app.ui.screens.match

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviemate.app.data.model.Film
import com.moviemate.app.di.LocalAppGraph
import com.moviemate.app.ui.components.CtaTone
import com.moviemate.app.ui.components.FilmPoster
import com.moviemate.app.ui.components.PillTag
import com.moviemate.app.ui.components.PrimaryCta
import com.moviemate.app.ui.components.SecondaryCta
import com.moviemate.app.ui.components.TagTone
import com.moviemate.app.ui.components.pressableCard
import com.moviemate.app.ui.core.ActionState
import com.moviemate.app.ui.core.UiStateHost
import com.moviemate.app.ui.core.factoryOf
import com.moviemate.app.ui.theme.BorderWidth
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Radius
import com.moviemate.app.ui.theme.Space

/**
 * Tonight's pick — the loop the whole product exists for.
 *
 * Six phases, each a different screen. The two asymmetric ones matter most:
 * "you're in, waiting on them" and "they're in, are you?" are the same document
 * seen from two sides, and showing the same thing to both would hide who the
 * app is actually waiting for.
 */
@Composable
fun MatchScreen(
    onRateWatched: (String) -> Unit,
    onScheduleWatch: (String) -> Unit,
) {
    val graph = LocalAppGraph.current
    val viewModel: MatchViewModel = viewModel(
        factory = factoryOf {
            MatchViewModel(graph.pairRepository, graph.filmRepository, graph.sessionStore)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()

    MatchScaffold {
        UiStateHost(state = state) { phase ->
            Column(verticalArrangement = Arrangement.spacedBy(Space.stack)) {
                when (phase) {
                    MatchPhase.NotYet -> Headline(
                        title = "NOT YET",
                        body = "Your pick lands at 9am. One film, chosen for both of you.",
                    )

                    is MatchPhase.NoMatches -> Headline(
                        title = "NO PICK TODAY",
                        body = phase.reason,
                    )

                    is MatchPhase.Suggested -> SuggestedPhase(
                        phase = phase,
                        busy = action.isRunning,
                        onCommit = { viewModel.commit(phase.match.id) },
                        onReject = { viewModel.reject(phase.match.id) },
                    )

                    is MatchPhase.Fallback -> FallbackPhase(
                        phase = phase,
                        busy = action.isRunning,
                        onChoose = { filmId -> viewModel.chooseFallback(phase.match.id, filmId) },
                    )

                    is MatchPhase.Confirmed -> ConfirmedPhase(
                        phase = phase,
                        busy = action.isRunning,
                        onSchedule = { onScheduleWatch(phase.match.id) },
                        onWatched = { viewModel.confirmWatched(phase.match.id) },
                    )

                    is MatchPhase.Watched -> WatchedPhase(
                        phase = phase,
                        onRate = { onRateWatched(phase.match.id) },
                    )
                }

                (action as? ActionState.Failed)?.let {
                    Text(
                        it.message,
                        style = MovieMateType.meta,
                        color = MovieMateTheme.colors.statusDecorative,
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchScaffold(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MovieMateTheme.colors.surfaceGround)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screenGutter, vertical = Space.screenTop),
    ) { content() }
}

@Composable
private fun Headline(title: String, body: String) {
    val colors = MovieMateTheme.colors
    Text(title, style = MovieMateType.megaHeadline, color = colors.textPrimary)
    Text(body, style = MovieMateType.body, color = colors.textSecondary)
}

/** The film card, shared by every phase that has a film to show. */
@Composable
private fun FilmCard(film: Film?, filmId: String, reason: String?) {
    val colors = MovieMateTheme.colors

    if (film == null) {
        // The match points at a film the cache has not returned. The pick is
        // real either way, so the actions stay usable rather than the whole
        // screen failing over a missing poster.
        Text("Film $filmId", style = MovieMateType.filmTitle, color = colors.textPrimary)
        return
    }

    FilmPoster(posterPath = film.posterPath, title = film.title)

    Spacer(Modifier.height(Space.stack))

    Text(film.title, style = MovieMateType.filmTitle, color = colors.textPrimary)
    Text(
        text = listOfNotNull(
            film.releaseYear.takeIf { it > 0 }?.toString(),
            film.runtime.takeIf { it > 0 }?.let { "${it} min" },
            film.genres.take(2).joinToString(" · ").takeIf { it.isNotBlank() },
        ).joinToString(" · "),
        style = MovieMateType.meta,
        color = colors.textSecondary,
    )

    if (!reason.isNullOrBlank()) {
        Spacer(Modifier.height(Space.stackTight))
        // The reason is why this pair got this film. Without it the pick reads
        // as arbitrary, which is the thing the recommender is there to avoid.
        Text(reason, style = MovieMateType.body, color = colors.textPrimary)
    }
}

@Composable
private fun SuggestedPhase(
    phase: MatchPhase.Suggested,
    busy: Boolean,
    onCommit: () -> Unit,
    onReject: () -> Unit,
) {
    val colors = MovieMateTheme.colors

    Text("TONIGHT", style = MovieMateType.megaHeadline, color = colors.textPrimary)

    Row(horizontalArrangement = Arrangement.spacedBy(Space.inline)) {
        PillTag("${phase.match.score}% match", TagTone.Accent)
        if (phase.attemptNumber > 1) {
            PillTag("Pick ${phase.attemptNumber} of 3", TagTone.Reward)
        }
    }

    Spacer(Modifier.height(Space.stackTight))

    FilmCard(phase.film, phase.match.filmId, phase.match.reason)

    Spacer(Modifier.height(Space.stack))

    when {
        phase.iCommitted -> Text(
            "You're in. Waiting on your partner.",
            style = MovieMateType.statCaption,
            color = colors.statusPending,
        )

        phase.partnerCommitted -> {
            Text(
                "They're in. Are you?",
                style = MovieMateType.statCaption,
                color = colors.textReward,
            )
            Spacer(Modifier.height(Space.stackTight))
            PrimaryCta(
                label = "We're in",
                onClick = onCommit,
                enabled = !busy,
                tone = CtaTone.Reward,
            )
            SecondaryCta(label = "Not feeling it", onClick = onReject)
        }

        else -> {
            PrimaryCta(label = "We're in", onClick = onCommit, enabled = !busy)
            SecondaryCta(label = "Not feeling it", onClick = onReject)
        }
    }

    // Rejecting after committing would silently revoke consent the partner has
    // already acted on, so the reject path closes once this user is in.
    if (phase.iCommitted) {
        Spacer(Modifier.height(Space.stackTight))
        Text(
            "Changed your mind? You'll get a fresh pick tomorrow.",
            style = MovieMateType.meta,
            color = colors.textSecondary,
        )
    }
}

/**
 * The 3-up screen — a last resort after three rejections, never the opening
 * move. Showing several options up front re-creates the disagreement the app
 * exists to remove (PRD §7.1).
 */
@Composable
private fun FallbackPhase(
    phase: MatchPhase.Fallback,
    busy: Boolean,
    onChoose: (String) -> Unit,
) {
    val colors = MovieMateTheme.colors

    Text("YOUR CALL", style = MovieMateType.megaHeadline, color = colors.textPrimary)
    Text(
        "None of the three landed. Here they all are — pick one together, or wait " +
            "for tomorrow's.",
        style = MovieMateType.body,
        color = colors.textSecondary,
    )

    Spacer(Modifier.height(Space.stack))

    phase.options.forEach { option ->
        val film = phase.films[option.filmId]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.card))
                .background(colors.surfaceRaised)
                .border(
                    BorderWidth.container,
                    colors.borderHairline,
                    RoundedCornerShape(Radius.card),
                )
                .pressableCard { if (!busy) onChoose(option.filmId) }
                .padding(Space.stackTight),
            horizontalArrangement = Arrangement.spacedBy(Space.stackTight),
        ) {
            Box(Modifier.width(POSTER_THUMB_WIDTH)) {
                FilmPoster(
                    posterPath = film?.posterPath,
                    title = film?.title ?: option.filmId,
                    cornerRadius = Radius.chip,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Space.inlineTight),
            ) {
                Text(
                    text = film?.title ?: "Film ${option.filmId}",
                    style = MovieMateType.statCaption,
                    color = colors.textPrimary,
                )
                Text(
                    text = "${option.score}% match",
                    style = MovieMateType.meta,
                    color = colors.textAccent,
                )
                if (option.reason.isNotBlank()) {
                    Text(option.reason, style = MovieMateType.meta, color = colors.textSecondary)
                }
            }
        }
        Spacer(Modifier.height(Space.stackTight))
    }
}

@Composable
private fun ConfirmedPhase(
    phase: MatchPhase.Confirmed,
    busy: Boolean,
    onSchedule: () -> Unit,
    onWatched: () -> Unit,
) {
    val colors = MovieMateTheme.colors

    Text("YOU'RE BOTH IN", style = MovieMateType.megaHeadline, color = colors.textPrimary)

    Spacer(Modifier.height(Space.stackTight))

    FilmCard(phase.film, phase.match.filmId, reason = null)

    Spacer(Modifier.height(Space.stack))

    if (phase.scheduledForMillis != null) {
        Text(
            text = "Set for ${formatWatchTime(phase.scheduledForMillis)}",
            style = MovieMateType.statCaption,
            color = colors.textReward,
        )
        SecondaryCta(label = "Change the time", onClick = onSchedule)
    } else {
        PrimaryCta(label = "Pick a time", onClick = onSchedule, tone = CtaTone.Reward)
    }

    Spacer(Modifier.height(Space.stackTight))

    // Always manual, never inferred from a calendar or a streaming service.
    PrimaryCta(label = "We watched it", onClick = onWatched, enabled = !busy)
}

@Composable
private fun WatchedPhase(phase: MatchPhase.Watched, onRate: () -> Unit) {
    val colors = MovieMateTheme.colors

    Text("HOW WAS IT?", style = MovieMateType.megaHeadline, color = colors.textPrimary)
    Text(
        "Rate it separately — the shared score is what teaches the next pick.",
        style = MovieMateType.body,
        color = colors.textSecondary,
    )

    Spacer(Modifier.height(Space.stack))

    FilmCard(phase.film, phase.match.filmId, reason = null)

    Spacer(Modifier.height(Space.stack))

    PrimaryCta(label = "Rate it", onClick = onRate, tone = CtaTone.Reward)
}

private val POSTER_THUMB_WIDTH = 72.dp
