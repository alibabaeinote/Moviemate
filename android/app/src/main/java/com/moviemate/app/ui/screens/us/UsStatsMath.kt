package com.moviemate.app.ui.screens.us

import kotlin.math.abs

/**
 * Pure math behind the Us screen's "journey" and "taste compatibility" — split
 * out from [UsViewModel] so the bucketing and averaging can be unit tested
 * without a Firestore round trip, the same way the backend keeps streak.ts
 * separate from the trigger that calls it.
 */
object UsStatsMath {

    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * How many films both partners have rated before a compatibility number
     * means anything. Below this, two or three lucky overlaps could swing the
     * average wildly — showing nothing is more honest than showing noise.
     */
    const val MIN_SHARED_RATED_FILMS = 3

    /**
     * Buckets watch timestamps into [weeks] trailing 7-day windows, oldest
     * first, so index 0 is the earliest week and the last index is the most
     * recent 7 days — the order a left-to-right trend chart reads in.
     */
    fun weeklyJourney(
        watchedAtMillis: List<Long>,
        weeks: Int,
        nowMillis: Long,
    ): List<Int> {
        val buckets = IntArray(weeks)
        for (watchedAt in watchedAtMillis) {
            val ageMs = nowMillis - watchedAt
            if (ageMs < 0) continue
            val weeksAgo = (ageMs / WEEK_MS).toInt()
            if (weeksAgo < weeks) {
                buckets[weeks - 1 - weeksAgo] += 1
            }
        }
        return buckets.toList()
    }

    /**
     * One rating: who made it, which film, and the 0-100 Taste Dial score.
     */
    data class RatingPoint(val filmId: String, val userId: String, val score: Double)

    /**
     * Average similarity — 100 minus the gap between their two scores — across
     * every film both partners have rated. Null before [MIN_SHARED_RATED_FILMS]
     * such films exist, same reasoning as the "both confirmed" match count:
     * a number nobody can act on is worse than no number.
     */
    fun tasteCompatibility(
        ratings: List<RatingPoint>,
        userA: String,
        userB: String,
    ): Int? {
        val similarities = ratings
            .groupBy { it.filmId }
            .values
            .mapNotNull { forFilm ->
                val a = forFilm.firstOrNull { it.userId == userA }?.score
                val b = forFilm.firstOrNull { it.userId == userB }?.score
                if (a == null || b == null) null else 100.0 - abs(a - b)
            }

        if (similarities.size < MIN_SHARED_RATED_FILMS) return null
        return similarities.average().let { Math.round(it).toInt() }
    }
}
