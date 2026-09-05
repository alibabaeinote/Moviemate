package com.moviemate.app.ui.screens.match

import androidx.lifecycle.viewModelScope
import com.moviemate.app.data.model.Film
import com.moviemate.app.data.repository.FilmRepository
import com.moviemate.app.data.repository.PairRepository
import com.moviemate.app.data.session.SessionStore
import com.moviemate.app.ui.core.MovieMateViewModel
import com.moviemate.app.ui.core.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Rating a film the pair actually watched.
 *
 * Distinct from onboarding rating in one field — `isInitialOnboarding` — but
 * the consequence is large: a post-watch rating feeds the pair's shared verdict
 * on that film and updates its mutualScore, while an onboarding rating counts
 * toward completing onboarding. Sending the wrong flag would either restart
 * someone's onboarding or silently skip the shared score.
 */
class RateWatchedViewModel(
    private val pairRepository: PairRepository,
    private val filmRepository: FilmRepository,
    private val sessionStore: SessionStore,
    private val matchId: String,
) : MovieMateViewModel() {

    private val _film = MutableStateFlow<UiState<Film?>>(UiState.Loading)
    val film: StateFlow<UiState<Film?>> = _film.asStateFlow()

    private val _score = MutableStateFlow(DEFAULT_SCORE)
    val score: StateFlow<Float> = _score.asStateFlow()

    private var filmId: String? = null

    init {
        viewModelScope.launch {
            val session = sessionStore.session.first { it?.pairId != null }
            val pairId = session?.pairId

            if (pairId == null) {
                _film.value = UiState.Failed("You're not in a pair yet.", retryable = false)
                return@launch
            }

            // The match holds the film, so it is read once here rather than
            // threaded through navigation as a second argument that could
            // disagree with it.
            val match = pairRepository.observeCurrentMatch(pairId).firstOrNull()
            val id = match?.filmId?.takeIf { it.isNotBlank() && match.id == matchId }

            if (id == null) {
                _film.value = UiState.Failed("Couldn't find that film.", retryable = false)
                return@launch
            }

            filmId = id
            _film.value = UiState.Content(filmRepository.getFilm(id))
        }
    }

    fun setScore(value: Float) {
        _score.value = value
    }

    fun submit() {
        val id = filmId ?: return
        runAction {
            val session = sessionStore.session.first { it?.pairId != null }
                ?: return@runAction Result.failure<Unit>(IllegalStateException("No pair."))
            val pairId = session.pairId
                ?: return@runAction Result.failure<Unit>(IllegalStateException("No pair."))

            pairRepository.submitRating(
                pairId = pairId,
                uid = session.uid,
                filmId = id,
                score = _score.value.toDouble(),
                isInitialOnboarding = false,
            )
        }
    }

    private companion object {
        const val DEFAULT_SCORE = 50f
    }
}
