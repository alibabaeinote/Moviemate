package com.moviemate.app.ui.screens.watchlist

import androidx.lifecycle.viewModelScope
import com.moviemate.app.data.model.WatchlistItem
import com.moviemate.app.data.repository.DeckFilm
import com.moviemate.app.data.repository.FilmRepository
import com.moviemate.app.data.repository.PairRepository
import com.moviemate.app.data.session.Session
import com.moviemate.app.data.session.SessionStore
import com.moviemate.app.ui.core.MovieMateViewModel
import com.moviemate.app.ui.core.UiState
import com.moviemate.app.ui.core.readableMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The list, already grouped and ordered for rendering. */
typealias WatchlistSections = Map<WatchlistSection, List<WatchlistRow>>

class WatchlistViewModel(
    private val pairRepository: PairRepository,
    private val filmRepository: FilmRepository,
    sessionStore: SessionStore,
) : MovieMateViewModel() {

    private val _state = MutableStateFlow<UiState<WatchlistSections>>(UiState.Loading)
    val state: StateFlow<UiState<WatchlistSections>> = _state.asStateFlow()

    private val _search = MutableStateFlow<UiState<List<DeckFilm>>?>(null)

    /** Null while the search sheet is closed. */
    val search: StateFlow<UiState<List<DeckFilm>>?> = _search.asStateFlow()

    private var session: Session? = null

    /**
     * Shared by the grouping and by the rows, which need `uid` for attribution.
     * Exposed so the screen does not collect the cold session flow again and
     * open a second set of Firestore listeners for the same data.
     */
    val sessionFlow: StateFlow<Session?> = sessionStore.session
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val items = sessionFlow
        .map { it?.pairId }
        .distinctUntilChanged()
        .flatMapLatest { pairId ->
            if (pairId == null) flowOf(emptyList()) else pairRepository.observeWatchlist(pairId)
        }

    init {
        viewModelScope.launch {
            combine(sessionFlow, items) { current, list -> current to list }
                .collect { (current, list) ->
                    session = current
                    render(current, list)
                }
        }
    }

    private suspend fun render(current: Session?, items: List<WatchlistItem>) {
        val pairId = current?.pairId
        if (current == null || pairId == null) {
            _state.value = UiState.Empty(
                headline = "No pair yet",
                detail = "Your shared list starts once your partner joins.",
            )
            return
        }

        if (items.isEmpty()) {
            _state.value = UiState.Empty(
                headline = "Nothing on the list",
                detail = "Films you both commit to land here, and you can add your own.",
            )
            return
        }

        val films = filmRepository.getFilms(items.map { it.filmId })

        // Only watched items show the two-marker axis, so only those pay for
        // the ratings read.
        val scoresByFilm = items
            .filter { it.status == "watched" }
            .associate { it.filmId to pairRepository.ratingsForFilm(pairId, it.filmId) }

        _state.value = UiState.Content(groupWatchlist(items, current, films, scoresByFilm))
    }

    /** "I'm in too", straight from the row (PRD §7.4 item 3). */
    fun commit(itemId: String) {
        val current = session ?: return
        val pairId = current.pairId ?: return
        runAction { pairRepository.commitToWatchlistItem(pairId, itemId, current.isUserA) }
    }

    fun remove(itemId: String) {
        val current = session ?: return
        val pairId = current.pairId ?: return
        runAction { pairRepository.deleteWatchlistItem(pairId, itemId) }
    }

    fun openSearch() {
        _search.value = UiState.Content(emptyList())
    }

    fun closeSearch() {
        _search.value = null
    }

    fun searchFilms(query: String) {
        if (query.isBlank()) {
            _search.value = UiState.Content(emptyList())
            return
        }
        _search.value = UiState.Loading
        viewModelScope.launch {
            _search.value = pairRepository.searchFilms(query).fold(
                onSuccess = { films ->
                    if (films.isEmpty()) {
                        UiState.Empty("Nothing found", "Try a different spelling.")
                    } else {
                        UiState.Content(films)
                    }
                },
                onFailure = { UiState.Failed(it.readableMessage()) },
            )
        }
    }

    /**
     * Add a searched film.
     *
     * The adder is committed on their own side from the start — proposing a
     * film to your partner is saying you want to watch it, and asking someone
     * to then tap "I'm in too" on their own suggestion is a step that means
     * nothing.
     */
    fun addFilm(film: DeckFilm) {
        val current = session ?: return
        val pairId = current.pairId ?: return
        runAction(onSuccess = { closeSearch() }) {
            pairRepository.addToWatchlist(pairId, current.uid, film.filmId, current.isUserA)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
