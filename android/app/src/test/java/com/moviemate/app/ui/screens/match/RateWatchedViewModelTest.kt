package com.moviemate.app.ui.screens.match

import com.moviemate.app.data.model.Film
import com.moviemate.app.data.model.Match
import com.moviemate.app.data.model.Pair as PairModel
import com.moviemate.app.data.model.User
import com.moviemate.app.data.repository.FakeFilmRepository
import com.moviemate.app.data.repository.FakePairRepository
import com.moviemate.app.data.repository.SubmitRatingCall
import com.moviemate.app.data.session.FakeSessionStore
import com.moviemate.app.data.session.Session
import com.moviemate.app.testutil.MainDispatcherRule
import com.moviemate.app.ui.core.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The one field this whole screen exists to get right is
 * `isInitialOnboarding = false` — per the class doc, sending `true` here
 * would restart someone's onboarding instead of updating the match's
 * mutualScore. Also worth pinning: this screen trusts the *current* match
 * document for its film, and refuses to rate a stale [matchId] that no
 * longer matches it.
 */
class RateWatchedViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val pairRepository = FakePairRepository()
    private val filmRepository = FakeFilmRepository()

    private val alice = Session(
        uid = "alice",
        user = User(uid = "alice", pairId = "p1"),
        pair = PairModel(id = "p1", userA = "alice", userB = "bob"),
    )

    private fun viewModel(session: Session?, matchId: String = "m1") =
        RateWatchedViewModel(pairRepository, filmRepository, FakeSessionStore(session), matchId)

    @Test
    fun `no pair yet fails without a retry, there is nothing to retry into`() {
        val viewModel = viewModel(session = null)

        val failed = viewModel.film.value as UiState.Failed
        assertEquals("You're not in a pair yet.", failed.message)
        assertTrue(!failed.retryable)
    }

    @Test
    fun `a matchId that does not match the pair's current match fails, not the stale film`() {
        filmRepository.put(Film(tmdbId = "f1", title = "Parasite"))
        pairRepository.setMatch("p1", Match(id = "m1", filmId = "f1"))

        val viewModel = viewModel(alice, matchId = "m-stale")

        val failed = viewModel.film.value as UiState.Failed
        assertEquals("Couldn't find that film.", failed.message)
    }

    @Test
    fun `loads the film from the pair's current match, keyed by the given matchId`() {
        filmRepository.put(Film(tmdbId = "f1", title = "Parasite"))
        pairRepository.setMatch("p1", Match(id = "m1", filmId = "f1"))

        val viewModel = viewModel(alice, matchId = "m1")

        val content = viewModel.film.value as UiState.Content
        assertEquals("Parasite", content.value?.title)
    }

    @Test
    fun `submit sends isInitialOnboarding false, never the onboarding flag`() {
        filmRepository.put(Film(tmdbId = "f1", title = "Parasite"))
        pairRepository.setMatch("p1", Match(id = "m1", filmId = "f1"))
        val viewModel = viewModel(alice, matchId = "m1")
        viewModel.setScore(82f)

        viewModel.submit()

        assertEquals(
            listOf(SubmitRatingCall(pairId = "p1", uid = "alice", filmId = "f1", score = 82.0, isInitialOnboarding = false)),
            pairRepository.submittedRatings,
        )
    }

    @Test
    fun `submit is a no-op when the film never resolved`() {
        val viewModel = viewModel(session = null)

        viewModel.submit()

        assertTrue(pairRepository.submittedRatings.isEmpty())
    }
}
