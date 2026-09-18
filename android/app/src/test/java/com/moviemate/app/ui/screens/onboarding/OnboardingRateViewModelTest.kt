package com.moviemate.app.ui.screens.onboarding

import com.moviemate.app.data.model.Pair as PairModel
import com.moviemate.app.data.model.User
import com.moviemate.app.data.repository.DeckFilm
import com.moviemate.app.data.repository.FakePairRepository
import com.moviemate.app.data.repository.TmdbGenre
import com.moviemate.app.data.session.FakeOnboardingDraftStore
import com.moviemate.app.data.session.FakeSessionStore
import com.moviemate.app.data.session.Session
import com.moviemate.app.testutil.MainDispatcherRule
import com.moviemate.app.ui.core.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [RateStep]'s own properties (canContinue/remaining/exhausted) are pure and
 * already covered by `RateStepTest`. What's worth pinning at the ViewModel
 * level is the split the class doc calls out: an unpaired score goes to the
 * draft store, a paired one goes straight to Firestore — and a paired write
 * that fails still lands in the draft store rather than being lost, so a
 * later flush can recover it.
 */
class OnboardingRateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val pairRepository = FakePairRepository()
    private val draftStore = FakeOnboardingDraftStore()

    private fun deckFilm(id: String) =
        DeckFilm(filmId = id, title = id, posterPath = null, genres = emptyList(), releaseYear = 2020, overview = "")

    private fun viewModel(session: Session?) =
        OnboardingRateViewModel(pairRepository, FakeSessionStore(session), draftStore)

    private fun startDeckOf(vararg filmIds: String, session: Session?): OnboardingRateViewModel {
        pairRepository.listGenresResult = Result.success(listOf(TmdbGenre(id = 1, name = "Drama")))
        pairRepository.getOnboardingFilmsResult = Result.success(filmIds.map { deckFilm(it) })
        val viewModel = viewModel(session)
        viewModel.toggleGenre(1)
        viewModel.loadDeck()
        return viewModel
    }

    @Test
    fun `no genres from the server renders Empty, not a blank picker`() {
        pairRepository.listGenresResult = Result.success(emptyList())

        val viewModel = viewModel(session = null)

        assertTrue(viewModel.state.value is UiState.Empty)
    }

    @Test
    fun `loadDeck is a no-op below MIN_GENRES, never sends an empty selection`() {
        pairRepository.listGenresResult = Result.success(listOf(TmdbGenre(id = 1, name = "Drama")))
        val viewModel = viewModel(session = null)

        viewModel.loadDeck()

        assertTrue(pairRepository.requestedGenreIds.isEmpty())
        assertTrue(viewModel.state.value is UiState.Content)
    }

    @Test
    fun `an unpaired session buffers the score in the draft store, not Firestore`() {
        val viewModel = startDeckOf("f1", "f2", session = null)

        viewModel.submitScore()

        assertTrue(pairRepository.submittedRatings.isEmpty())
        assertEquals(listOf("f1"), draftStore.ratings().map { it.filmId })
    }

    @Test
    fun `a paired session writes straight to Firestore as an onboarding rating`() {
        val session = Session(uid = "alice", user = User(uid = "alice", pairId = "p1"), pair = PairModel(id = "p1", userA = "alice"))
        val viewModel = startDeckOf("f1", session = session)
        viewModel.setScore(77f)

        viewModel.submitScore()

        assertEquals(
            listOf("f1"),
            pairRepository.submittedRatings.map { it.filmId },
        )
        assertEquals(77.0, pairRepository.submittedRatings.single().score, 0.0)
        assertTrue(pairRepository.submittedRatings.single().isInitialOnboarding)
        assertTrue("a successful write should not also sit in the buffer", draftStore.ratings().isEmpty())
    }

    @Test
    fun `a paired write that fails still lands in the draft store, so it is recoverable`() {
        pairRepository.submitRatingResult = Result.failure(IllegalStateException("offline"))
        val session = Session(uid = "alice", user = User(uid = "alice", pairId = "p1"), pair = PairModel(id = "p1", userA = "alice"))
        val viewModel = startDeckOf("f1", session = session)

        viewModel.submitScore()

        assertEquals(listOf("f1"), draftStore.ratings().map { it.filmId })
    }

    @Test
    fun `the deck advances immediately on submit, without waiting on the write`() {
        val viewModel = startDeckOf("f1", "f2", session = null)

        viewModel.submitScore()

        val step = (viewModel.state.value as UiState.Content).value as RateStep.RateDeck
        assertEquals(1, step.index)
        assertEquals(1, step.recorded)
    }

    @Test
    fun `skipFilm advances without recording a score anywhere`() {
        val viewModel = startDeckOf("f1", "f2", session = null)

        viewModel.skipFilm()

        assertTrue(draftStore.ratings().isEmpty())
        assertTrue(pairRepository.submittedRatings.isEmpty())
        val step = (viewModel.state.value as UiState.Content).value as RateStep.RateDeck
        assertEquals(1, step.index)
        assertEquals(0, step.recorded)
    }

    @Test
    fun `ratingsSoFar seeds recorded from the pair's rating count once paired`() {
        val session = Session(
            uid = "alice",
            user = User(uid = "alice", pairId = "p1", ratingCount = 4),
            pair = PairModel(id = "p1", userA = "alice"),
        )
        val viewModel = startDeckOf("f1", session = session)

        val step = (viewModel.state.value as UiState.Content).value as RateStep.RateDeck
        assertEquals(4, step.recorded)
    }

    @Test
    fun `extendDeck folds in fresh films but drops any already on the deck`() {
        val viewModel = startDeckOf("f1", session = null)
        pairRepository.getOnboardingFilmsResult = Result.success(listOf(deckFilm("f1"), deckFilm("f2")))

        viewModel.extendDeck()

        val step = (viewModel.state.value as UiState.Content).value as RateStep.RateDeck
        assertEquals(listOf("f1", "f2"), step.films.map { it.filmId })
    }
}
