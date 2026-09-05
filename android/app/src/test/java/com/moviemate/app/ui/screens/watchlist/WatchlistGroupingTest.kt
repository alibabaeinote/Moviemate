package com.moviemate.app.ui.screens.watchlist

import com.google.firebase.Timestamp
import com.moviemate.app.data.model.CommitStatus
import com.moviemate.app.data.model.Pair
import com.moviemate.app.data.model.User
import com.moviemate.app.data.model.WatchlistItem
import com.moviemate.app.data.session.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which section a shared-list item lands in, read from one partner's side.
 *
 * The stored status has three values but the middle one splits in two once you
 * know who is looking, and getting that backwards puts an "I'm in too" button
 * in front of the person who already pressed it.
 */
class WatchlistGroupingTest {

    private fun sessionFor(uid: String) = Session(
        uid = uid,
        user = User(uid = uid, pairId = "p1"),
        pair = Pair(id = "p1", userA = "alice", userB = "bob"),
    )

    private val alice = sessionFor("alice")
    private val bob = sessionFor("bob")

    private fun item(
        id: String = "i1",
        filmId: String = "f1",
        status: String = "waiting",
        commitA: Boolean = false,
        commitB: Boolean = false,
        addedBy: String = "alice",
        source: String = "manual_search",
        mutualScore: Double? = null,
        addedAtSeconds: Long = 1_700_000_000L,
    ) = WatchlistItem(
        id = id,
        filmId = filmId,
        addedBy = addedBy,
        addedAt = Timestamp(addedAtSeconds, 0),
        source = source,
        status = status,
        commitStatus = CommitStatus(commitA, commitB),
        mutualScore = mutualScore,
    )

    @Test
    fun `waiting splits by who has not answered`() {
        val onlyAliceIn = item(commitA = true)

        assertEquals(WatchlistSection.WaitingOnThem, sectionOf(onlyAliceIn, alice))
        assertEquals(WatchlistSection.WaitingOnYou, sectionOf(onlyAliceIn, bob))
    }

    @Test
    fun `neither in is waiting on whoever is looking`() {
        val nobodyIn = item()

        assertEquals(WatchlistSection.WaitingOnYou, sectionOf(nobodyIn, alice))
        assertEquals(WatchlistSection.WaitingOnYou, sectionOf(nobodyIn, bob))
    }

    @Test
    fun `ready and watched read the same from both sides`() {
        val ready = item(status = "ready", commitA = true, commitB = true)
        val watched = item(status = "watched", commitA = true, commitB = true)

        assertEquals(WatchlistSection.Ready, sectionOf(ready, alice))
        assertEquals(WatchlistSection.Ready, sectionOf(ready, bob))
        assertEquals(WatchlistSection.Watched, sectionOf(watched, alice))
        assertEquals(WatchlistSection.Watched, sectionOf(watched, bob))
    }

    /** PRD §7.4 item 5: a list of good evenings, not a reverse-chronological log. */
    @Test
    fun `watched items sort by shared satisfaction, not by date`() {
        val items = listOf(
            item(id = "recent-poor", filmId = "f1", status = "watched",
                mutualScore = 30.0, addedAtSeconds = 1_700_000_900L),
            item(id = "old-great", filmId = "f2", status = "watched",
                mutualScore = 91.0, addedAtSeconds = 1_700_000_100L),
        )

        val watched = groupWatchlist(items, alice, films = emptyMap())[WatchlistSection.Watched]

        assertEquals(listOf("old-great", "recent-poor"), watched?.map { it.id })
    }

    @Test
    fun `unanswered items sort newest first, because the newest task is the live one`() {
        val items = listOf(
            item(id = "older", filmId = "f1", addedAtSeconds = 1_700_000_100L),
            item(id = "newer", filmId = "f2", addedAtSeconds = 1_700_000_900L),
        )

        val waiting = groupWatchlist(items, alice, films = emptyMap())[WatchlistSection.WaitingOnYou]

        assertEquals(listOf("newer", "older"), waiting?.map { it.id })
    }

    @Test
    fun `empty sections are dropped rather than rendered as empty headings`() {
        val grouped = groupWatchlist(listOf(item()), alice, films = emptyMap())

        assertEquals(setOf(WatchlistSection.WaitingOnYou), grouped.keys)
    }

    @Test
    fun `a watched item with no shared score still sorts, below the scored ones`() {
        val items = listOf(
            item(id = "unscored", filmId = "f1", status = "watched", mutualScore = null),
            item(id = "scored", filmId = "f2", status = "watched", mutualScore = 40.0),
        )

        val watched = groupWatchlist(items, alice, films = emptyMap())[WatchlistSection.Watched]

        assertEquals(listOf("scored", "unscored"), watched?.map { it.id })
    }

    @Test
    fun `attribution names the source, and reads correctly from both sides`() {
        val fromMatch = WatchlistRow(
            item = item(source = "match"),
            film = null,
            section = WatchlistSection.Ready,
        )
        assertEquals("From a match", fromMatch.attribution(alice))

        val addedByAlice = WatchlistRow(
            item = item(addedBy = "alice"),
            film = null,
            section = WatchlistSection.WaitingOnYou,
        )
        assertEquals("Added by you", addedByAlice.attribution(alice))
        assertEquals("Added by your partner", addedByAlice.attribution(bob))
    }

    @Test
    fun `sections keep their declared order so Ready stays at the top`() {
        val items = listOf(
            item(id = "watched", filmId = "f1", status = "watched"),
            item(id = "ready", filmId = "f2", status = "ready"),
            item(id = "mine", filmId = "f3"),
        )

        val order = groupWatchlist(items, alice, films = emptyMap()).keys.toList()

        assertTrue(order.indexOf(WatchlistSection.Ready) < order.indexOf(WatchlistSection.Watched))
    }
}
