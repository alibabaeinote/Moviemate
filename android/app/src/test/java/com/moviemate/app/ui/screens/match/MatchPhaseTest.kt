package com.moviemate.app.ui.screens.match

import com.google.firebase.Timestamp
import com.moviemate.app.data.model.CommitStatus
import com.moviemate.app.data.model.Match
import com.moviemate.app.data.model.Pair
import com.moviemate.app.data.model.ShortlistEntry
import com.moviemate.app.data.model.User
import com.moviemate.app.data.session.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading a match document from one user's side.
 *
 * The asymmetric cases are the point: the same document means "waiting on them"
 * to one partner and "they're in, are you?" to the other, and getting that
 * backwards tells someone the app is waiting on a person who has already acted.
 */
class MatchPhaseTest {

    private val stamp = Timestamp(1_700_000_000L, 0)

    private fun sessionFor(uid: String) = Session(
        uid = uid,
        user = User(uid = uid, pairId = "p1"),
        pair = Pair(id = "p1", userA = "alice", userB = "bob"),
    )

    private val alice = sessionFor("alice")
    private val bob = sessionFor("bob")

    private fun match(
        filmId: String = "f1",
        status: String = "suggested",
        attemptNumber: Int = 1,
        commitA: Boolean = false,
        commitB: Boolean = false,
        bothConfirmedAt: Timestamp? = null,
        watchedConfirmedAt: Timestamp? = null,
        fallbackUnlocked: Boolean = false,
        noMatchesReason: String? = null,
        shortlist: List<ShortlistEntry> = emptyList(),
        scheduledFor: Timestamp? = null,
    ) = Match(
        id = "m1",
        filmId = filmId,
        score = 82,
        reason = "You both rate slow-burn thrillers highly.",
        status = status,
        attemptNumber = attemptNumber,
        commitStatus = CommitStatus(commitA, commitB),
        bothConfirmedAt = bothConfirmedAt,
        watchedConfirmedAt = watchedConfirmedAt,
        fallbackUnlocked = fallbackUnlocked,
        noMatchesReason = noMatchesReason,
        shortlist = shortlist,
        scheduledFor = scheduledFor,
    )

    @Test
    fun `no document means the day has not started`() {
        assertEquals(MatchPhase.NotYet, matchPhaseOf(null, alice, film = null))
    }

    @Test
    fun `an empty filmId is a no-match day, not a broken document`() {
        val phase = matchPhaseOf(
            match(filmId = "", status = "dismissed", noMatchesReason = "Nothing scored high enough."),
            alice,
            film = null,
        )
        assertEquals(MatchPhase.NoMatches("Nothing scored high enough."), phase)
    }

    @Test
    fun `commit flags are read from the caller's own side`() {
        val onlyAliceIn = match(commitA = true)

        val forAlice = matchPhaseOf(onlyAliceIn, alice, film = null) as MatchPhase.Suggested
        assertTrue(forAlice.iCommitted)
        assertFalse(forAlice.partnerCommitted)

        val forBob = matchPhaseOf(onlyAliceIn, bob, film = null) as MatchPhase.Suggested
        assertFalse(forBob.iCommitted)
        assertTrue(forBob.partnerCommitted)
    }

    @Test
    fun `both confirmed opens the scheduling phase`() {
        val phase = matchPhaseOf(
            match(commitA = true, commitB = true, bothConfirmedAt = stamp),
            alice,
            film = null,
        )
        assertTrue(phase is MatchPhase.Confirmed)
    }

    /**
     * A watched match still has both commit flags set and a bothConfirmedAt, so
     * testing the lifecycle forwards would report it as merely confirmed and
     * offer "We watched it" on something already watched.
     */
    @Test
    fun `watched outranks confirmed`() {
        val phase = matchPhaseOf(
            match(
                commitA = true,
                commitB = true,
                bothConfirmedAt = stamp,
                watchedConfirmedAt = stamp,
                status = "watched",
            ),
            alice,
            film = null,
        )
        assertTrue(phase is MatchPhase.Watched)
    }

    @Test
    fun `an unlocked fallback shows the shortlist rather than the spent film`() {
        val options = listOf(
            ShortlistEntry("f1", 80, "one"),
            ShortlistEntry("f2", 74, "two"),
            ShortlistEntry("f3", 71, "three"),
        )
        val phase = matchPhaseOf(
            match(status = "dismissed", fallbackUnlocked = true, shortlist = options),
            alice,
            film = null,
        ) as MatchPhase.Fallback

        assertEquals(options, phase.options)
    }

    /**
     * Dismissed without the fallback flag means the pair passed on the day —
     * distinct from the fallback being available, and it must not fall through
     * to Suggested and re-offer a film they already rejected.
     */
    @Test
    fun `dismissed without a fallback ends the day`() {
        val phase = matchPhaseOf(
            match(status = "dismissed", fallbackUnlocked = false),
            alice,
            film = null,
        )
        assertTrue(phase is MatchPhase.NoMatches)
    }

    @Test
    fun `the attempt number carries through for the sequence label`() {
        val phase = matchPhaseOf(match(attemptNumber = 3), alice, film = null)
            as MatchPhase.Suggested
        assertEquals(3, phase.attemptNumber)
    }
}
