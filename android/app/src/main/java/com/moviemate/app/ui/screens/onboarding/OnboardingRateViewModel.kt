package com.moviemate.app.ui.screens.onboarding

import androidx.lifecycle.viewModelScope
import com.moviemate.app.data.repository.DeckFilm
import com.moviemate.app.data.repository.PairRepository
import com.moviemate.app.data.repository.TmdbGenre
import com.moviemate.app.data.session.OnboardingDraftStore
import com.moviemate.app.data.session.SessionStore
import com.moviemate.app.ui.core.MovieMateViewModel
import com.moviemate.app.ui.core.UiState
import com.moviemate.app.ui.core.readableMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * The two stages of the rating deck: pick genres, then rate films.
 *
 * Modelled as one screen with two steps rather than two routes, because the
 * genre pick has no meaning on its own — backing out of the deck should return
 * to the genres, not leave a half-built profile behind.
 */
sealed interface RateStep {

    data class PickGenres(
        val genres: List<TmdbGenre>,
        val selected: Set<Int>,
    ) : RateStep {
        val canContinue: Boolean get() = selected.size >= OnboardingConfig.MIN_GENRES
    }

    data class RateDeck(
        val films: List<DeckFilm>,
        val index: Int,
        val score: Float,
        val recorded: Int,
        /** Carried forward so an exhausted deck can be topped up from the same picks. */
        val genreIds: List<Int>,
    ) : RateStep {
        val current: DeckFilm? get() = films.getOrNull(index)
        val remaining: Int get() = (OnboardingConfig.RATING_TARGET - recorded).coerceAtLeast(0)

        /** True once the deck runs out before the target does. */
        val exhausted: Boolean get() = current == null && remaining > 0
    }

    /** Target reached. The scores are in the draft store, not yet in Firestore. */
    data object Done : RateStep
}

/**
 * Where a score goes depends on whether a pair exists yet, which depends on
 * which order this person took: sign up → rate → invite, or sign up → join →
 * rate. Both are real, so both are handled.
 */
class OnboardingRateViewModel(
    private val pairRepository: PairRepository,
    private val sessionStore: SessionStore,
    private val draftStore: OnboardingDraftStore,
) : MovieMateViewModel() {

    private val _state = MutableStateFlow<UiState<RateStep>>(UiState.Loading)
    val state: StateFlow<UiState<RateStep>> = _state.asStateFlow()

    init {
        loadGenres()
    }

    fun loadGenres() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = pairRepository.listGenres().fold(
                onSuccess = { genres ->
                    if (genres.isEmpty()) {
                        UiState.Empty(
                            headline = "No genres to show",
                            detail = "We couldn't reach the film database. Try again in a moment.",
                        )
                    } else {
                        UiState.Content(RateStep.PickGenres(genres, emptySet()))
                    }
                },
                onFailure = { UiState.Failed(it.readableMessage()) },
            )
        }
    }

    fun toggleGenre(genreId: Int) {
        val step = currentStep<RateStep.PickGenres>() ?: return
        val selected = if (genreId in step.selected) {
            step.selected - genreId
        } else {
            step.selected + genreId
        }
        _state.value = UiState.Content(step.copy(selected = selected))
    }

    fun loadDeck() {
        val step = currentStep<RateStep.PickGenres>() ?: return
        if (!step.canContinue) return

        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = pairRepository
                .getOnboardingFilms(step.selected.toList())
                .fold(
                    onSuccess = { films ->
                        if (films.isEmpty()) {
                            UiState.Empty(
                                headline = "Nothing to rate yet",
                                detail = "Try picking a couple more genres.",
                            )
                        } else {
                            // Anything already recorded — from an interrupted
                            // run — counts, so a returning user is not asked to
                            // start the ten again. Whichever store holds them.
                            UiState.Content(
                                RateStep.RateDeck(
                                    films = films,
                                    index = 0,
                                    score = OnboardingConfig.DEFAULT_SCORE,
                                    recorded = ratingsSoFar(),
                                    genreIds = step.selected.toList(),
                                ),
                            )
                        }
                    },
                    onFailure = { UiState.Failed(it.readableMessage()) },
                )
        }
    }

    fun setScore(score: Float) {
        val step = currentStep<RateStep.RateDeck>() ?: return
        _state.value = UiState.Content(step.copy(score = score))
    }

    /**
     * Record the current score and move on.
     *
     * The UI advances immediately rather than waiting on the write. A rating is
     * not something the user can get wrong, and making someone watch a spinner
     * ten times to complete onboarding is a worse failure than a rating that
     * has to be re-sent — which the draft store handles for the unpaired case.
     */
    fun submitScore() {
        val step = currentStep<RateStep.RateDeck>() ?: return
        val film = step.current ?: return
        val score = step.score.toDouble()

        advance(step, recorded = step.recorded + 1)

        viewModelScope.launch {
            val session = sessionStore.session.firstOrNull()
            val pairId = session?.pairId

            if (pairId == null) {
                // No pair yet — buffer it. PairSetupViewModel flushes the buffer
                // as soon as the user invites or joins.
                draftStore.record(film.filmId, score)
            } else {
                pairRepository.submitRating(
                    pairId = pairId,
                    uid = session.uid,
                    filmId = film.filmId,
                    score = score,
                    isInitialOnboarding = true,
                ).onFailure {
                    // Falling back to the buffer keeps the score recoverable:
                    // the next visit to invite/join flushes it.
                    draftStore.record(film.filmId, score)
                }
            }
        }
    }

    /**
     * "Haven't seen it" — move on without a score.
     *
     * A guessed score is worse than no score: the taste profile treats every
     * rating as ground truth, so an invented one is noise that survives for the
     * life of the account.
     */
    fun skipFilm() {
        val step = currentStep<RateStep.RateDeck>() ?: return
        advance(step, recorded = step.recorded)
    }

    private fun advance(step: RateStep.RateDeck, recorded: Int) {
        _state.value = if (recorded >= OnboardingConfig.RATING_TARGET) {
            UiState.Content(RateStep.Done)
        } else {
            UiState.Content(
                step.copy(
                    index = step.index + 1,
                    score = OnboardingConfig.DEFAULT_SCORE,
                    recorded = recorded,
                ),
            )
        }
    }

    /** Deck ran out before the target — fetch a fresh one from the same genres. */
    fun extendDeck() {
        val current = currentStep<RateStep.RateDeck>() ?: return
        viewModelScope.launch {
            pairRepository.getOnboardingFilms(current.genreIds).onSuccess { films ->
                val step = currentStep<RateStep.RateDeck>() ?: return@onSuccess
                val seen = step.films.map { it.filmId }.toSet()
                val fresh = films.filterNot { it.filmId in seen }
                if (fresh.isEmpty()) return@onSuccess
                _state.value = UiState.Content(
                    step.copy(films = step.films + fresh, score = OnboardingConfig.DEFAULT_SCORE),
                )
            }
        }
    }

    /**
     * How many onboarding ratings this user already has.
     *
     * `ratingCount` is every rating, including post-watch ones — but a user
     * who has watched anything is long past onboarding, so during this flow the
     * two counts are the same.
     */
    private suspend fun ratingsSoFar(): Int {
        val session = sessionStore.session.firstOrNull()
        return if (session?.pairId == null) draftStore.count() else session.ratingCount
    }

    private inline fun <reified S : RateStep> currentStep(): S? =
        (_state.value as? UiState.Content)?.value as? S
}
