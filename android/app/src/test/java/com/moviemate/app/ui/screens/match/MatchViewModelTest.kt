package com.moviemate.app.ui.screens.match

import com.google.firebase.Timestamp
import com.moviemate.app.data.model.Film
import com.moviemate.app.data.model.Match
import com.moviemate.app.data.model.Pair as PairModel
import com.moviemate.app.data.model.ShortlistEntry
import com.moviemate.app.data.model.User
import com.moviemate.app.data.repository.ChooseFallbackCall
import com.moviemate.app.data.repository.CommitMatchCall
import com.moviemate.app.data.repository.ConfirmWatchedCall
import com.moviemate.app.data.repository.FakeFilmRepository
import com.moviemate.app.data.repository.FakePairRepository
import com.moviemate.app.data.repository.ScheduleWatchCall
import com.moviemate.app.data.session.FakeSessionStore
import com.moviemate.app.data.session.Session
import com.moviemate.app.testutil.MainDispatcherRule
import com.moviemate.app.ui.core.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [matchPhaseOf] already owns the phase-selection logic (see `MatchPhaseTest`).
 * What's worth pinning at the ViewModel level is the wiring around it: that a
 * write carries the tapping user's own seat and uid rather than a guess, and
 * that the film fetch only happens for the phase that actually needs it —
 * both are easy to get backwards without a compiler error.
 */
class MatchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val pairRepository = FakePairRepository()
    private val filmRepository = FakeFilmRepository()

    private val alice = Session(
        uid = "alice",
        user = User(uid = "alice", pairId = "p1"),
        pair = PairModel(id = "p1", userA = "alice", userB = "bob", aBothOnboarded = true),
    )

    private fun viewModel(session: Session?) =
        MatchViewModel(pairRepository, filmRepository, FakeSessionStore(session))

    @Test
    fun `no pair yet renders Empty`() {
        val viewModel = viewModel(session = null)

        assertTrue(viewModel.state.value is UiState.Empty)
    }

    @Test
    fun `a pair mid-onboarding renders WaitingForPartner as Content, not Empty`() {
        val session = alice.copy(pair = alice.pair!!.copy(aBothOnboarded = false))

        val viewModel = viewModel(session)

        val phase = (viewModel.state.value as UiState.Content).value
        assertTrue(phase is MatchPhase.WaitingForPartner)
    }

    @Test
    fun `no match document yet renders NotYet`() {
        val viewModel = viewModel(alice)

        val phase = (viewModel.state.value as UiState.Content).value
        assertTrue(phase is MatchPhase.NotYet)
    }

    @Test
    fun `a suggested match fetches its one film, not the whole shortlist`() {
        filmRepository.put(Film(tmdbId = "f1", title = "Parasite"))
        pairRepository.setMatch("p1", Match(id = "m1", filmId = "f1", fallbackUnlocked = false))

        val viewModel = viewModel(alice)

        val phase = (viewModel.state.value as UiState.Content).value as MatchPhase.Suggested
        assertEquals("Parasite", phase.film?.title)
    }

    @Test
    fun `a fallback match fetches the shortlist films, keyed by id`() {
        filmRepository.put(Film(tmdbId = "f2", title = "Oldboy"))
        filmRepository.put(Film(tmdbId = "f3", title = "Burning"))
        pairRepository.setMatch(
            "p1",
            Match(
                id = "m1",
                fallbackUnlocked = true,
                shortlist = listOf(ShortlistEntry(filmId = "f2"), ShortlistEntry(filmId = "f3")),
            ),
        )

        val viewModel = viewModel(alice)

        val phase = (viewModel.state.value as UiState.Content).value as MatchPhase.Fallback
        assertEquals(setOf("f2", "f3"), phase.films.keys)
    }

    @Test
    fun `commit sends this match's id and the caller's own seat`() {
        pairRepository.setMatch("p1", Match(id = "m1", filmId = "f1"))
        val viewModel = viewModel(alice)

        viewModel.commit("m1")

        assertEquals(
            listOf(CommitMatchCall(pairId = "p1", matchId = "m1", isUserA = true)),
            pairRepository.committedMatches,
        )
    }

    @Test
    fun `reject advances the sequence for the pair, not a per-user flag`() {
        pairRepository.setMatch("p1", Match(id = "m1", filmId = "f1"))
        val viewModel = viewModel(alice)

        viewModel.reject("m1")

        assertEquals(listOf("m1"), pairRepository.rejectedMatches)
    }

    @Test
    fun `chooseFallback sends the tapped film's id, not the first shortlist entry`() {
        pairRepository.setMatch(
            "p1",
            Match(id = "m1", fallbackUnlocked = true, shortlist = listOf(ShortlistEntry(filmId = "f2"))),
        )
        val viewModel = viewModel(alice)

        viewModel.chooseFallback("m1", "f3")

        assertEquals(
            listOf(ChooseFallbackCall(pairId = "p1", matchId = "m1", filmId = "f3")),
            pairRepository.chosenFallbacks,
        )
    }

    @Test
    fun `schedule sends the chosen time as given`() {
        pairRepository.setMatch("p1", Match(id = "m1", bothConfirmedAt = Timestamp(1_700_000_000L, 0)))
        val viewModel = viewModel(alice)

        viewModel.schedule("m1", 1_800_000_000_000L)

        assertEquals(
            listOf(ScheduleWatchCall(pairId = "p1", matchId = "m1", scheduledForMillis = 1_800_000_000_000L)),
            pairRepository.scheduledWatches,
        )
    }

    @Test
    fun `confirmWatched sends the confirming user's own uid, not the partner's`() {
        pairRepository.setMatch("p1", Match(id = "m1", bothConfirmedAt = Timestamp(1_700_000_000L, 0)))
        val viewModel = viewModel(alice)

        viewModel.confirmWatched("m1")

        assertEquals(
            listOf(ConfirmWatchedCall(pairId = "p1", matchId = "m1", uid = "alice")),
            pairRepository.confirmedWatched,
        )
    }

    @Test
    fun `commit is a no-op with no session, rather than crashing on a null pair`() {
        val viewModel = viewModel(session = null)

        viewModel.commit("m1")

        assertTrue(pairRepository.committedMatches.isEmpty())
    }
}
