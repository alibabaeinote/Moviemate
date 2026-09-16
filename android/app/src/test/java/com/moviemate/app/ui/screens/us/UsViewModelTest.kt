package com.moviemate.app.ui.screens.us

import com.moviemate.app.data.model.NotificationSettings
import com.moviemate.app.data.model.Pair as PairModel
import com.moviemate.app.data.model.Rating
import com.moviemate.app.data.model.User
import com.moviemate.app.data.repository.FakeAuthRepository
import com.moviemate.app.data.repository.FakePairRepository
import com.moviemate.app.data.repository.PairStatsInputs
import com.moviemate.app.data.repository.PairTotals
import com.moviemate.app.data.repository.UpdateNotificationSettingsCall
import com.moviemate.app.data.session.FakeSessionStore
import com.moviemate.app.data.session.Session
import com.moviemate.app.testutil.MainDispatcherRule
import com.moviemate.app.ui.core.UiState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [UsViewModel] wires three independently-refreshing collectors around one
 * [com.moviemate.app.data.session.SessionStore] — the interesting failure
 * mode isn't any single field, it's whether totals (from `pairTotals`) and
 * extended stats (from `statsInputs`) both actually land in the same
 * rendered [UsStats], not just whichever refreshed last.
 */
class UsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val pairRepository = FakePairRepository()
    private val authRepository = FakeAuthRepository()

    private fun viewModel(session: Session?) =
        UsViewModel(pairRepository, authRepository, FakeSessionStore(session))

    @Test
    fun `no pair yet renders Empty instead of guessing at zeros`() {
        val session = Session(uid = "u1", user = User(uid = "u1", pairId = null), pair = null)

        val viewModel = viewModel(session)

        assertTrue(viewModel.state.value is UiState.Empty)
    }

    @Test
    fun `a paired session renders totals from pairTotals, not from the pair document`() {
        pairRepository.pairTotalsResult = PairTotals(matches = 14, watched = 11)
        val session = Session(
            uid = "u1",
            user = User(uid = "u1", pairId = "p1", name = "Ali"),
            pair = PairModel(id = "p1", userA = "u1", userB = "u2", streakCount = 6),
        )

        val viewModel = viewModel(session)

        val content = viewModel.state.value as UiState.Content
        assertEquals(14, content.value.matches)
        assertEquals(11, content.value.watched)
        assertEquals(6, content.value.streak)
        assertTrue(content.value.isUserA)
        assertTrue(content.value.partnerJoined)
    }

    @Test
    fun `journey and compatibility come from statsInputs, computed through UsStatsMath`() {
        val now = System.currentTimeMillis()
        pairRepository.statsInputsResult = PairStatsInputs(
            watchedAtMillis = listOf(now),
            ratings = listOf(
                Rating(userId = "u1", filmId = "f1", score = 80.0),
                Rating(userId = "u2", filmId = "f1", score = 84.0),
                Rating(userId = "u1", filmId = "f2", score = 60.0),
                Rating(userId = "u2", filmId = "f2", score = 64.0),
                Rating(userId = "u1", filmId = "f3", score = 90.0),
                Rating(userId = "u2", filmId = "f3", score = 88.0),
            ),
        )
        val session = Session(
            uid = "u1",
            user = User(uid = "u1", pairId = "p1"),
            pair = PairModel(id = "p1", userA = "u1", userB = "u2"),
        )

        val viewModel = viewModel(session)

        val content = viewModel.state.value as UiState.Content
        assertEquals(1, content.value.journeyWeeks.last())
        assertTrue(content.value.compatibilityPercent != null)
    }

    @Test
    fun `updateNotifications writes to authRepository under the signed-in uid, not a guess`() {
        val session = Session(
            uid = "u1",
            user = User(uid = "u1", pairId = "p1"),
            pair = PairModel(id = "p1", userA = "u1", userB = "u2"),
        )
        val viewModel = viewModel(session)
        val newSettings = NotificationSettings(dailyMatch = false)

        viewModel.updateNotifications(newSettings)

        assertEquals(
            listOf(UpdateNotificationSettingsCall("u1", newSettings)),
            authRepository.updatedNotificationSettings,
        )
    }

    @Test
    fun `signOut delegates straight to authRepository`() {
        val viewModel = viewModel(session = null)

        viewModel.signOut()

        assertTrue(authRepository.signedOut)
    }
}
