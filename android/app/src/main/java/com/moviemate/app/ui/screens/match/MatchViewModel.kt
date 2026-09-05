package com.moviemate.app.ui.screens.match

import androidx.lifecycle.viewModelScope
import com.moviemate.app.data.model.Film
import com.moviemate.app.data.model.Match
import com.moviemate.app.data.repository.FilmRepository
import com.moviemate.app.data.repository.PairRepository
import com.moviemate.app.data.session.Session
import com.moviemate.app.data.session.SessionStore
import com.moviemate.app.ui.core.MovieMateViewModel
import com.moviemate.app.ui.core.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MatchViewModel(
    private val pairRepository: PairRepository,
    private val filmRepository: FilmRepository,
    private val sessionStore: SessionStore,
) : MovieMateViewModel() {

    private val _state = MutableStateFlow<UiState<MatchPhase>>(UiState.Loading)
    val state: StateFlow<UiState<MatchPhase>> = _state.asStateFlow()

    /**
     * The current session, kept for the commit writes.
     *
     * `isUserA` decides which of the two commit flags a "We're in" tap sets, and
     * the rules reject a write to the other one — so this must come from the
     * pair document rather than being assumed.
     */
    private var session: Session? = null

    /**
     * One upstream collector for the session, shared by both consumers below.
     *
     * SessionStore.session is cold, so collecting it twice would open a second
     * users/{uid} and pairs/{pairId} listener — two sets of billed reads for
     * one screen.
     */
    private val sessionFlow = sessionStore.session
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * Keyed on pairId alone. Restarting the match listener every time the user
     * document changes — and `lastActiveAt` changes on every launch — would
     * tear down and re-open a Firestore listener for nothing.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val matches = sessionFlow
        .map { it?.pairId }
        .distinctUntilChanged()
        .flatMapLatest { pairId ->
            if (pairId == null) flowOf(null) else pairRepository.observeCurrentMatch(pairId)
        }

    init {
        // Combined rather than collected separately: the phase is a function of
        // both, and rendering on whichever arrived last would show a match read
        // from the wrong side of the pair for a frame — which is the side that
        // decides what "We're in" writes.
        viewModelScope.launch {
            combine(sessionFlow, matches) { session, match -> session to match }
                .collect { (current, match) ->
                    session = current
                    render(current, match)
                }
        }
    }

    /**
     * Rebuild the phase, fetching only the film documents the phase needs.
     *
     * The suggested and confirmed phases need one film; the fallback screen
     * needs three. Fetching the shortlist eagerly would mean three reads a day
     * for every pair to render a screen most of them never see.
     */
    private suspend fun render(current: Session?, match: Match?) {
        if (current == null || !current.isPaired) {
            _state.value = UiState.Empty(
                headline = "No pair yet",
                detail = "Invite your partner to start getting a pick each night.",
            )
            return
        }

        if (match == null) {
            _state.value = UiState.Content(MatchPhase.NotYet)
            return
        }

        val film = match.filmId.takeIf { it.isNotBlank() }?.let { filmRepository.getFilm(it) }
        val shortlistFilms: Map<String, Film> = if (match.fallbackUnlocked) {
            filmRepository.getFilms(match.shortlist.map { it.filmId })
        } else {
            emptyMap()
        }

        _state.value = UiState.Content(matchPhaseOf(match, current, film, shortlistFilms))
    }

    /** "We're in" — sets only this user's own flag. */
    fun commit(matchId: String) {
        val current = session ?: return
        val pairId = current.pairId ?: return
        runAction { pairRepository.commitToMatch(pairId, matchId, current.isUserA) }
    }

    /**
     * "Not feeling it" — advances the sequence for both.
     *
     * Deliberately not a per-user flag: rejecting is a shared decision, and
     * waiting for the second person to also say no would leave a film neither
     * of them wants sitting on the screen all evening.
     */
    fun reject(matchId: String) {
        val current = session ?: return
        val pairId = current.pairId ?: return
        runAction { pairRepository.rejectMatch(pairId, matchId) }
    }

    fun chooseFallback(matchId: String, filmId: String) {
        val current = session ?: return
        val pairId = current.pairId ?: return
        runAction { pairRepository.chooseFallbackFilm(pairId, matchId, filmId) }
    }

    fun schedule(matchId: String, whenMillis: Long) {
        val current = session ?: return
        val pairId = current.pairId ?: return
        runAction { pairRepository.scheduleWatch(pairId, matchId, whenMillis) }
    }

    /**
     * "We watched it" — either partner may confirm.
     *
     * Requiring both to tap would be friction for its own sake: it records
     * something that already happened rather than asking for a decision.
     */
    fun confirmWatched(matchId: String) {
        val current = session ?: return
        val pairId = current.pairId ?: return
        runAction { pairRepository.confirmWatched(pairId, matchId) }
    }

    private companion object {
        /** Keeps listeners alive across a rotation without leaking them. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
