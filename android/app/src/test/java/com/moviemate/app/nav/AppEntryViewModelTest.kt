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
        onboardingComplete: Boolean = false,
        pair: Pair? = null,
    ) = Session(
        uid = uid,
        user = User(uid = uid, pairId = pairId, onboardingComplete = onboardingComplete),
        pair = pair,
    )

    private fun pair(
        userA: String = "u1",
        userB: String? = "u2",
        bothOnboarded: Boolean = false,
    ) = Pair(
        id = "p1",
        userA = userA,
        userB = userB,
        inviteCode = "ABC123",
    ).also { it.aBothOnboarded = bothOnboarded }

    @Test
    fun `signed out goes to welcome`() {
        assertEquals(
            Routes.WELCOME,
            AppEntryViewModel.startRouteFor(session = null, draftCount = 0),
        )
    }

    @Test
    fun `a live pair goes straight to the match`() {
        val state = session(pairId = "p1", onboardingComplete = true, pair = pair(bothOnboarded = true))
        assertEquals(Routes.MATCH, AppEntryViewModel.startRouteFor(state, draftCount = 0))
    }

    @Test
    fun `own onboarding done but partner still rating waits`() {
        val state = session(pairId = "p1", onboardingComplete = true, pair = pair())
        assertEquals(
            Routes.WAITING_FOR_PARTNER,
            AppEntryViewModel.startRouteFor(state, draftCount = 0),
        )
    }

    @Test
    fun `paired but still rating returns to the deck`() {
        val state = session(pairId = "p1", onboardingComplete = false, pair = pair())
        assertEquals(
            Routes.ONBOARDING_RATE,
            AppEntryViewModel.startRouteFor(state, draftCount = 0),
        )
    }

    @Test
    fun `unpaired with a full draft goes to invite, not back through the deck`() {
        assertEquals(
            Routes.INVITE_PARTNER,
            AppEntryViewModel.startRouteFor(
                session(),
                draftCount = OnboardingConfig.RATING_TARGET,
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
            ),
        )
    }

    /**
     * The server's flag wins over anything the client can infer. A pair can be
     * live while this user's own `onboardingComplete` has not propagated yet,
     * and sending them to the deck in that moment would be wrong.
     */
    @Test
    fun `bothOnboarded outranks a stale local onboarding flag`() {
        val state = session(
            pairId = "p1",
            onboardingComplete = false,
            pair = pair(bothOnboarded = true),
        )
        assertEquals(Routes.MATCH, AppEntryViewModel.startRouteFor(state, draftCount = 0))
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
