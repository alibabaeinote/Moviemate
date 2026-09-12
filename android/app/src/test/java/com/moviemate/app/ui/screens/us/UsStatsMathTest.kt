package com.moviemate.app.ui.screens.us

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The bucketing and averaging behind the Us screen's journey and
 * compatibility numbers, isolated from Firestore so the edge cases — an empty
 * week, a lone shared rating — are cheap to pin down.
 */
class UsStatsMathTest {

    private val week = 7L * 24 * 60 * 60 * 1000
    private val now = 1_700_000_000_000L

    @Test
    fun `buckets a watch into the most recent week`() {
        val result = UsStatsMath.weeklyJourney(listOf(now), weeks = 6, nowMillis = now)
        assertEquals(listOf(0, 0, 0, 0, 0, 1), result)
    }

    @Test
    fun `buckets an older watch into an earlier week, oldest first`() {
        val fiveWeeksAgo = now - 5 * week
        val result = UsStatsMath.weeklyJourney(listOf(fiveWeeksAgo), weeks = 6, nowMillis = now)
        assertEquals(listOf(1, 0, 0, 0, 0, 0), result)
    }

    @Test
    fun `drops a watch older than the window`() {
        val result = UsStatsMath.weeklyJourney(listOf(now - 10 * week), weeks = 6, nowMillis = now)
        assertEquals(listOf(0, 0, 0, 0, 0, 0), result)
    }

    @Test
    fun `counts multiple watches in the same week`() {
        val result = UsStatsMath.weeklyJourney(listOf(now, now - 1), weeks = 6, nowMillis = now)
        assertEquals(2, result.last())
    }

    @Test
    fun `compatibility is null below the minimum shared ratings`() {
        val ratings = listOf(
            UsStatsMath.RatingPoint("f1", "alice", 80.0),
            UsStatsMath.RatingPoint("f1", "bob", 75.0),
        )
        assertNull(UsStatsMath.tasteCompatibility(ratings, "alice", "bob"))
    }

    @Test
    fun `compatibility averages similarity once the minimum is met`() {
        // 100 - |80-75| = 95, 100 - |40-60| = 80, 100 - |90-90| = 100 -> avg 91.67 -> 92
        val ratings = listOf(
            UsStatsMath.RatingPoint("f1", "alice", 80.0),
            UsStatsMath.RatingPoint("f1", "bob", 75.0),
            UsStatsMath.RatingPoint("f2", "alice", 40.0),
            UsStatsMath.RatingPoint("f2", "bob", 60.0),
            UsStatsMath.RatingPoint("f3", "alice", 90.0),
            UsStatsMath.RatingPoint("f3", "bob", 90.0),
        )
        assertEquals(92, UsStatsMath.tasteCompatibility(ratings, "alice", "bob"))
    }

    @Test
    fun `a film only one partner rated does not count toward the minimum`() {
        val ratings = listOf(
            UsStatsMath.RatingPoint("f1", "alice", 80.0),
            UsStatsMath.RatingPoint("f1", "bob", 75.0),
            UsStatsMath.RatingPoint("f2", "alice", 40.0),
            UsStatsMath.RatingPoint("f2", "bob", 60.0),
            // Only alice rated this one.
            UsStatsMath.RatingPoint("f3", "alice", 90.0),
        )
        assertNull(UsStatsMath.tasteCompatibility(ratings, "alice", "bob"))
    }

    @Test
    fun `perfect agreement scores 100`() {
        val ratings = listOf("f1", "f2", "f3").flatMap { filmId ->
            listOf(
                UsStatsMath.RatingPoint(filmId, "alice", 70.0),
                UsStatsMath.RatingPoint(filmId, "bob", 70.0),
            )
        }
        assertEquals(100, UsStatsMath.tasteCompatibility(ratings, "alice", "bob"))
    }
}
