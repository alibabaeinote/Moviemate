package com.moviemate.app.ui.screens.onboarding

import com.google.firebase.Timestamp
import com.moviemate.app.data.model.Pair as PairModel
import com.moviemate.app.data.model.User
import com.moviemate.app.data.repository.FakePairRepository
import com.moviemate.app.data.repository.InviteInfo
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
 * The two paths through this screen share one requirement: the buffered
 * onboarding scores have to land in whichever pair now exists. What's worth
 * pinning is that recovering an already-existing pair (e.g. after a rotation)
 * takes the same flush path as creating a fresh one — and that flushing is
 * skipped, not errored, when there is nothing buffered.
 */
class PairSetupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val pairRepository = FakePairRepository()
    private val draftStore = FakeOnboardingDraftStore()

    private fun viewModel(sessionStore: FakeSessionStore) =
        PairSetupViewModel(pairRepository, sessionStore, draftStore)

    @Test
    fun `an already-existing pair's code is recovered, not treated as an error`() {
        val paired = Session(
            uid = "alice",
            user = User(uid = "alice", pairId = "p1"),
            pair = PairModel(id = "p1", userA = "alice", inviteCode = "XYZ999"),
        )
        val sessionStore = FakeSessionStore(paired)
        val viewModel = viewModel(sessionStore)

        viewModel.startInvite()

        val content = viewModel.invite.value as UiState.Content
        assertEquals("XYZ999", content.value.inviteCode)
        assertEquals(0, pairRepository.createPairCallCount)
        assertTrue(viewModel.ready.value)
    }

    @Test
    fun `creating a new pair flushes the buffered draft once the session catches up`() {
        draftStore.record("f1", 80.0)
        draftStore.record("f2", 60.0)
        val unpaired = Session(uid = "alice", user = User(uid = "alice", pairId = null), pair = null)
        val sessionStore = FakeSessionStore(unpaired)
        pairRepository.createPairResult = Result.success(
            InviteInfo(pairId = "p1", inviteCode = "NEW111", expiresAtMillis = 0L),
        )
        val viewModel = viewModel(sessionStore)

        viewModel.startInvite()
        // The client's own users/{uid} listener lands a moment after the
        // callable returns — simulated here by pushing the now-paired session.
        sessionStore.emit(
            Session(uid = "alice", user = User(uid = "alice", pairId = "p1"), pair = PairModel(id = "p1", userA = "alice")),
        )

        assertEquals(1, pairRepository.createPairCallCount)
        val content = viewModel.invite.value as UiState.Content
        assertEquals("NEW111", content.value.inviteCode)
        assertEquals(
            setOf("f1" to 80.0, "f2" to 60.0),
            pairRepository.submittedRatings.map { it.filmId to it.score }.toSet(),
        )
        assertTrue(pairRepository.submittedRatings.all { it.isInitialOnboarding })
        assertEquals(0, draftStore.count())
        assertTrue(viewModel.ready.value)
    }

    @Test
    fun `join sends the code trimmed and uppercased, never as the user typed it`() {
        val session = Session(uid = "alice", user = User(uid = "alice", pairId = null), pair = null)
        val viewModel = viewModel(FakeSessionStore(session))

        viewModel.join("  abc123  ")

        assertEquals(listOf("ABC123"), pairRepository.joinedInviteCodes)
    }

    @Test
    fun `a failed join reports the error and never touches the draft`() {
        draftStore.record("f1", 80.0)
        pairRepository.joinPairResult = Result.failure(IllegalStateException("This invite has already been used"))
        val session = Session(uid = "alice", user = User(uid = "alice", pairId = null), pair = null)
        val viewModel = viewModel(FakeSessionStore(session))

        viewModel.join("ABC123")

        assertEquals(1, draftStore.count())
    }

    @Test
    fun `flushing an empty draft still marks ready, without a pointless flush call`() {
        val paired = Session(
            uid = "alice",
            user = User(uid = "alice", pairId = "p1"),
            pair = PairModel(id = "p1", userA = "alice", inviteCode = "XYZ999"),
        )
        val viewModel = viewModel(FakeSessionStore(paired))

        viewModel.startInvite()

        assertTrue(pairRepository.submittedRatings.isEmpty())
        assertTrue(viewModel.ready.value)
    }
}
