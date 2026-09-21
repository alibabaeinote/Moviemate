package com.moviemate.app.nav

import com.moviemate.app.data.model.Pair
import com.moviemate.app.data.model.User
import com.moviemate.app.data.session.Session
import com.moviemate.app.ui.screens.onboarding.OnboardingConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The start-destination decision, which is the one piece of navigation that can
 * silently strand someone: send a paired user back to the rating deck and they
 * re-rate ten films into a profile that is already complete.
 */
class AppEntryViewModelTest {

    private fun session(
        uid: String = "u1",
        pairId: String? = null,
        pair: Pair? = null,
    ) = Session(
        uid = uid,
        user = User(uid = uid, pairId = pairId),
        pair = pair,
    )

    private fun pair(
        userA: String = "u1",
        userB: String? = "u2",
    ) = Pair(
        id = "p1",
        userA = userA,
        userB = userB,
        inviteCode = "ABC123",
    )

    @Test
    fun `signed out goes to welcome`() {
        assertEquals(
            Routes.WELCOME,
            AppEntryViewModel.startRouteFor(
                session = null,
                draftCount = 0,
                ownOnboardingComplete = false,
                bothOnboarded = false,
            ),
        )
    }

    @Test
    fun `a live pair goes straight to the match`() {
        val state = session(pairId = "p1", pair = pair())
        assertEquals(
            Routes.MATCH,
            AppEntryViewModel.startRouteFor(
                state,
                draftCount = 0,
                ownOnboardingComplete = true,
                bothOnboarded = true,
            ),
        )
    }

    @Test
    fun `own onboarding done but partner still rating still goes to Match`() {
        // The Match tab shows the "waiting on partner" copy itself
        // (MatchPhase.WaitingForPartner) — there is no separate holding screen.
        val state = session(pairId = "p1", pair = pair())
        assertEquals(
            Routes.MATCH,
            AppEntryViewModel.startRouteFor(
                state,
                draftCount = 0,
                ownOnboardingComplete = true,
                bothOnboarded = false,
            ),
        )
    }

    @Test
    fun `paired but still rating returns to the deck`() {
        val state = session(pairId = "p1", pair = pair())
        assertEquals(
            Routes.ONBOARDING_RATE,
            AppEntryViewModel.startRouteFor(
                state,
                draftCount = 0,
                ownOnboardingComplete = false,
                bothOnboarded = false,
            ),
        )
    }

    @Test
    fun `unpaired with a full draft goes to invite, not back through the deck`() {
        assertEquals(
            Routes.INVITE_PARTNER,
            AppEntryViewModel.startRouteFor(
                session(),
                draftCount = OnboardingConfig.RATING_TARGET,
                ownOnboardingComplete = false,
                bothOnboarded = false,
            ),
        )
    }

    @Test
    fun `unpaired with a partial draft resumes the deck`() {
        assertEquals(
            Routes.ONBOARDING_RATE,
            AppEntryViewModel.startRouteFor(
                session(),
                draftCount = OnboardingConfig.RATING_TARGET - 1,
                ownOnboardingComplete = false,
                bothOnboarded = false,
            ),
        )
    }

    /**
     * The live bothOnboarded check wins over anything the client can infer
     * about its own side. A pair can be live while this user's own onboarding
     * count has not been re-read yet, and sending them to the deck in that
     * moment would be wrong.
     */
    @Test
    fun `bothOnboarded outranks a stale local onboarding flag`() {
        val state = session(pairId = "p1", pair = pair())
        assertEquals(
            Routes.MATCH,
            AppEntryViewModel.startRouteFor(
                state,
                draftCount = 0,
                ownOnboardingComplete = false,
                bothOnboarded = true,
            ),
        )
    }

    @Test
    fun `a session is only settled once the user and its pair have arrived`() {
        assertFalse(Session(uid = "u1", user = null, pair = null).isSettled)
        assertTrue(session().isSettled)
        assertFalse(session(pairId = "p1", pair = null).isSettled)
        assertTrue(session(pairId = "p1", pair = pair()).isSettled)
    }

    @Test
    fun `isUserA reads the seat rather than assuming one`() {
        assertTrue(session(uid = "u1", pairId = "p1", pair = pair(userA = "u1")).isUserA)
        assertFalse(session(uid = "u2", pairId = "p1", pair = pair(userA = "u1")).isUserA)
        // No pair loaded: refuse to guess, because this decides which commit
        // flag a "We're in" tap writes.
        assertFalse(session(uid = "u1", pairId = "p1", pair = null).isUserA)
    }
}
