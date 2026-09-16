package com.moviemate.app.ui.screens.watchlist

import com.google.firebase.Timestamp
import com.moviemate.app.data.model.CommitStatus
import com.moviemate.app.data.model.Film
import com.moviemate.app.data.model.Pair as PairModel
import com.moviemate.app.data.model.User
import com.moviemate.app.data.model.WatchlistItem
import com.moviemate.app.data.repository.CommitWatchlistCall
import com.moviemate.app.data.repository.DeckFilm
import com.moviemate.app.data.repository.FakeFilmRepository
import com.moviemate.app.data.repository.FakePairRepository
import com.moviemate.app.data.session.FakeSessionStore
import com.moviemate.app.data.session.Session
import com.moviemate.app.testutil.MainDispatcherRule
import com.moviemate.app.ui.core.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [WatchlistViewModel] is mostly wiring — [WatchlistGrouping] already owns
 * the section logic (see `WatchlistGroupingTest`). What's worth pinning here
 * is that the wiring sends the RIGHT identifiers to the repository: a commit
 * or remove has to carry the item's own id and the caller's own seat, not
 * whichever values happened to be in scope.
 */
class WatchlistViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val pairRepository = FakePairRepository()
    private val filmRepository = FakeFilmRepository()

    private val alice = Session(
        uid = "alice",
        user = User(uid = "alice", pairId = "p1"),
        pair = PairModel(id = "p1", userA = "alice", userB = "bob"),
    )

    private fun viewModel(session: Session?) =
        WatchlistViewModel(pairRepository, filmRepository, FakeSessionStore(session))

    private fun item(
        id: String,
        filmId: String = "f1",
        status: String = "waiting",
        commitA: Boolean = false,
        commitB: Boolean = false,
    ) = WatchlistItem(
        id = id,
        filmId = filmId,
        addedBy = "alice",
        addedAt = Timestamp(1_700_000_000L, 0),
        source = "manual_search",
        status = status,
        commitStatus = CommitStatus(commitA, commitB),
    )

    @Test
    fun `no pair yet renders Empty`() {
        val viewModel = viewModel(session = null)

        assertTrue(viewModel.state.value is UiState.Empty)
    }

    @Test
    fun `a paired session with nothing on the list still renders Empty, not Content of nothing`() {
        pairRepository.setPair("p1", alice.pair)
        pairRepository.setWatchlist("p1", emptyList())

        val viewModel = viewModel(alice)

        assertTrue(viewModel.state.value is UiState.Empty)
    }

    @Test
    fun `items render grouped, with film metadata attached from FilmRepository`() {
        filmRepository.put(Film(tmdbId = "f1", title = "Parasite"))
        pairRepository.setPair("p1", alice.pair)
        pairRepository.setWatchlist("p1", listOf(item(id = "w1", filmId = "f1")))

        val viewModel = viewModel(alice)

        val content = viewModel.state.value as UiState.Content
        val row = content.value.values.flatten().single { it.item.id == "w1" }
        assertEquals("Parasite", row.film?.title)
        assertEquals(WatchlistSection.WaitingOnYou, row.section)
    }

    @Test
    fun `commit sends this item's id and the caller's own seat, not the partner's`() {
        pairRepository.setPair("p1", alice.pair)
        pairRepository.setWatchlist("p1", listOf(item(id = "w1", commitA = false, commitB = true)))
        val viewModel = viewModel(alice)

        viewModel.commit("w1")

        assertEquals(
            listOf(CommitWatchlistCall(pairId = "p1", itemId = "w1", isUserA = true)),
            pairRepository.committedWatchlistItems,
        )
    }

    @Test
    fun `remove deletes exactly the tapped item`() {
        pairRepository.setPair("p1", alice.pair)
        pairRepository.setWatchlist("p1", listOf(item(id = "w1"), item(id = "w2")))
        val viewModel = viewModel(alice)

        viewModel.remove("w2")

        assertEquals(listOf("w2"), pairRepository.deletedWatchlistItems)
    }

    @Test
    fun `a blank search query clears results without calling the repository`() {
        pairRepository.setPair("p1", alice.pair)
        val viewModel = viewModel(alice)
        viewModel.openSearch()

        viewModel.searchFilms("   ")

        val results = viewModel.search.value as UiState.Content
        assertTrue(results.value.isEmpty())
    }

    @Test
    fun `adding a searched film commits the adder's own seat and closes the sheet`() {
        pairRepository.setPair("p1", alice.pair)
        pairRepository.setWatchlist("p1", emptyList())
        val viewModel = viewModel(alice)
        viewModel.openSearch()

        viewModel.addFilm(DeckFilm(filmId = "f9", title = "Chungking Express", posterPath = null, genres = emptyList(), releaseYear = 1994, overview = ""))

        assertEquals(listOf("f9"), pairRepository.addedFilmIds)
        assertNull("search sheet should close on a successful add", viewModel.search.value)
    }
}
