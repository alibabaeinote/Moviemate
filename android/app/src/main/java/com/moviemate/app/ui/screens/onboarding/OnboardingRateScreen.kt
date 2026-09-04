package com.moviemate.app.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviemate.app.di.LocalAppGraph
import com.moviemate.app.ui.components.FilmPoster
import com.moviemate.app.ui.components.PrimaryCta
import com.moviemate.app.ui.components.SecondaryCta
import com.moviemate.app.ui.components.TasteDialSlider
import com.moviemate.app.ui.components.pressableCard
import com.moviemate.app.ui.core.UiStateHost
import com.moviemate.app.ui.core.factoryOf
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Radius
import com.moviemate.app.ui.theme.Space

/**
 * Stage one of onboarding: teach the recommender what this person likes.
 *
 * Nothing here is written to Firestore. The scores go into the draft store and
 * are flushed once a pair exists — see OnboardingDraftStore for why that
 * ordering is forced rather than chosen.
 */
@Composable
fun OnboardingRateScreen(onFinished: () -> Unit) {
    val graph = LocalAppGraph.current
    val viewModel: OnboardingRateViewModel = viewModel(
        factory = factoryOf {
            OnboardingRateViewModel(
                graph.pairRepository,
                graph.sessionStore,
                graph.onboardingDraftStore,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    OnboardingScaffold {
        UiStateHost(state = state, onRetry = viewModel::loadGenres) { step ->
            when (step) {
                is RateStep.PickGenres -> GenreStep(
                    step = step,
                    onToggle = viewModel::toggleGenre,
                    onContinue = viewModel::loadDeck,
                )

                is RateStep.RateDeck -> DeckStep(
                    step = step,
                    onScoreChange = viewModel::setScore,
                    onSubmit = viewModel::submitScore,
                    onSkip = viewModel::skipFilm,
                    onExtend = viewModel::extendDeck,
                )

                RateStep.Done -> LaunchedEffect(Unit) { onFinished() }
            }
        }
    }
}

/** Shared page frame for the onboarding chain. */
@Composable
internal fun OnboardingScaffold(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MovieMateTheme.colors.surfaceGround)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screenGutter, vertical = Space.screenTop),
        verticalArrangement = Arrangement.spacedBy(Space.stack),
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreStep(
    step: RateStep.PickGenres,
    onToggle: (Int) -> Unit,
    onContinue: () -> Unit,
) {
    val colors = MovieMateTheme.colors

    Text("WHAT DO YOU LIKE?", style = MovieMateType.megaHeadline, color = colors.textPrimary)
    Text(
        "Pick a few. We'll build your rating deck from them — you can change your mind later.",
        style = MovieMateType.body,
        color = colors.textSecondary,
    )

    Spacer(Modifier.height(Space.stackTight))

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.inline),
        verticalArrangement = Arrangement.spacedBy(Space.inline),
    ) {
        step.genres.forEach { genre ->
            val selected = genre.id in step.selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(if (selected) colors.actionPrimaryFill else colors.surfaceRaised)
                    .pressableCard { onToggle(genre.id) }
                    .padding(horizontal = Space.stackTight, vertical = Space.stackTight),
            ) {
                Text(
                    text = genre.name,
                    style = MovieMateType.tag,
                    color = if (selected) colors.textOnFill else colors.textPrimary,
                )
            }
        }
    }

    Spacer(Modifier.height(Space.sectionGap))

    PrimaryCta(
        label = if (step.canContinue) "Start rating" else "Pick at least one",
        onClick = onContinue,
        enabled = step.canContinue,
    )
}

@Composable
private fun DeckStep(
    step: RateStep.RateDeck,
    onScoreChange: (Float) -> Unit,
    onSubmit: () -> Unit,
    onSkip: () -> Unit,
    onExtend: () -> Unit,
) {
    val colors = MovieMateTheme.colors
    val film = step.current

    if (film == null) {
        // Deck ran out before the target. Offering more films is the only
        // useful move — the alternative is a dead end mid-onboarding.
        Text("A few more?", style = MovieMateType.megaHeadline, color = colors.textPrimary)
        Text(
            "That's the end of this deck and we're still ${step.remaining} short. " +
                "Here are some more.",
            style = MovieMateType.body,
            color = colors.textSecondary,
        )
        PrimaryCta(label = "Show me more films", onClick = onExtend)
        return
    }

    Text(
        text = "${step.recorded} OF ${OnboardingConfig.RATING_TARGET}",
        style = MovieMateType.overline,
        color = colors.textSecondary,
    )

    ProgressRail(
        fraction = step.recorded.toFloat() / OnboardingConfig.RATING_TARGET,
    )

    Spacer(Modifier.height(Space.stackTight))

    FilmPoster(posterPath = film.posterPath, title = film.title)

    Text(film.title, style = MovieMateType.filmTitle, color = colors.textPrimary)
    Text(
        text = listOfNotNull(
            film.releaseYear.takeIf { it > 0 }?.toString(),
            film.genres.take(2).joinToString(" · ").takeIf { it.isNotBlank() },
        ).joinToString(" · "),
        style = MovieMateType.meta,
        color = colors.textSecondary,
    )

    Spacer(Modifier.height(Space.stackTight))

    TasteDialSlider(score = step.score, onScoreChange = onScoreChange)

    Spacer(Modifier.height(Space.stackTight))

    PrimaryCta(label = "Rate it", onClick = onSubmit)
    SecondaryCta(label = "Haven't seen it", onClick = onSkip)
}

/** Thin progress bar, reusing the dial's track treatment. */
@Composable
private fun ProgressRail(fraction: Float) {
    val colors = MovieMateTheme.colors
    val clamped = fraction.coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(Radius.pill))
            .background(colors.surfaceSunken),
    ) {
        if (clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .height(6.dp)
                    .background(colors.actionPrimaryFill),
            )
        }
    }
}
