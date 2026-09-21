package com.moviemate.app.data.recommendation

import java.time.Instant
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ported from web/match-engine.test.js, itself ported from
 * functions/test/{tasteProfile,scoring,streak}.test.ts — the same logic,
 * the same edge cases, now pinned for the Kotlin copy. The two documented
 * deviations carry over unchanged: no country signal, and advanceStreak has
 * no timezone parameter (always UTC calendar days — see the last streak
 * test, which pins that deviation explicitly).
 */
class MatchEngineTest {

    private fun rated(filmId: String, score: Double, genres: List<String> = listOf("Drama"), releaseYear: Int = 2021) =
        RatedFilm(filmId = filmId, genres = genres, releaseYear = releaseYear, score = score)

    private fun candidate(
        filmId: String,
        genres: List<String> = listOf("Sci-Fi"),
        releaseYear: Int = 2021,
        tmdbRating: Double = 7.0,
    ) = ScorableFilm(filmId = filmId, genres = genres, releaseYear = releaseYear, tmdbRating = tmdbRating)

    // ---------- decadeKey ----------

    @Test
    fun `decadeKey buckets modern years by decade`() {
        assertEquals("2020s", decadeKey(2024))
        assertEquals("2020s", decadeKey(2020))
        assertEquals("2010s", decadeKey(2019))
        assertEquals("2000s", decadeKey(2000))
    }

    @Test
    fun `decadeKey collapses everything before 2000 into one bucket`() {
        assertEquals("pre-2000", decadeKey(1999))
        assertEquals("pre-2000", decadeKey(1954))
    }

    // ---------- buildTasteProfile ----------

    @Test
    fun `buildTasteProfile averages scores per genre across films`() {
        val profile = buildTasteProfile(
            listOf(
                rated("1", 80.0, genres = listOf("Sci-Fi")),
                rated("2", 60.0, genres = listOf("Sci-Fi")),
                rated("3", 20.0, genres = listOf("Comedy")),
            ),
        )

        assertEquals(70.0, profile.genreAffinity["Sci-Fi"]!!, 1e-9)
        assertEquals(20.0, profile.genreAffinity["Comedy"]!!, 1e-9)
        assertEquals(3, profile.sampleSize)
    }

    @Test
    fun `buildTasteProfile credits a multi-genre film to every one of its genres`() {
        val profile = buildTasteProfile(listOf(rated("1", 90.0, genres = listOf("Sci-Fi", "Drama"))))

        assertEquals(90.0, profile.genreAffinity["Sci-Fi"]!!, 1e-9)
        assertEquals(90.0, profile.genreAffinity["Drama"]!!, 1e-9)
    }

    @Test
    fun `buildTasteProfile builds era affinity alongside genre`() {
        val profile = buildTasteProfile(
            listOf(
                rated("1", 85.0, releaseYear = 2022),
                rated("2", 45.0, releaseYear = 1994),
            ),
        )

        assertEquals(85.0, profile.eraAffinity["2020s"]!!, 1e-9)
        assertEquals(45.0, profile.eraAffinity["pre-2000"]!!, 1e-9)
    }

    @Test
    fun `buildTasteProfile returns an empty profile for a user who has rated nothing`() {
        val profile = buildTasteProfile(emptyList())
        assertTrue(profile.genreAffinity.isEmpty())
        assertEquals(0, profile.sampleSize)
    }

    // ---------- predictScore ----------

    @Test
    fun `predictScore applies the configured genre-era signal weights`() {
        val profile = buildTasteProfile(listOf(rated("1", 100.0, genres = listOf("Sci-Fi"), releaseYear = 2021)))
        assertEquals(100.0, predictScore(profile, candidate("c1")), 1e-6)
    }

    @Test
    fun `predictScore weights genre more heavily than era`() {
        val genreOnly = buildTasteProfile(listOf(rated("1", 100.0, genres = listOf("Sci-Fi"), releaseYear = 1980)))
        val eraOnly = buildTasteProfile(listOf(rated("1", 100.0, genres = listOf("Horror"), releaseYear = 2021)))
        val target = candidate("c1")

        assertTrue(predictScore(genreOnly, target) > predictScore(eraOnly, target))
    }

    @Test
    fun `predictScore falls back to the neutral midpoint for unseen dimensions`() {
        val profile = buildTasteProfile(emptyList())
        assertEquals(AlgorithmConfig.NEUTRAL_AFFINITY, predictScore(profile, candidate("c1")), 1e-6)
    }

    @Test
    fun `predictScore stays inside 0-100 for the extremes of the Taste Dial`() {
        val hated = buildTasteProfile(listOf(rated("1", 0.0, genres = listOf("Sci-Fi"), releaseYear = 2021)))
        val loved = buildTasteProfile(listOf(rated("1", 100.0, genres = listOf("Sci-Fi"), releaseYear = 2021)))
        val target = candidate("c1")

        assertTrue(predictScore(hated, target) >= 0.0)
        assertTrue(predictScore(loved, target) <= 100.0)
    }

    // ---------- scoreCandidate ----------

    @Test
    fun `scoreCandidate penalises divergence - a shared 70-70 beats a lopsided 95-45`() {
        val sciFiFan = buildTasteProfile(
            listOf(
                rated("a1", 95.0, genres = listOf("Sci-Fi")),
                rated("a2", 95.0, genres = listOf("Sci-Fi"), releaseYear = 2022),
            ),
        )
        val sciFiSkeptic = buildTasteProfile(
            listOf(
                rated("b1", 45.0, genres = listOf("Sci-Fi")),
                rated("b2", 45.0, genres = listOf("Sci-Fi"), releaseYear = 2022),
            ),
        )
        val bothLukewarm = buildTasteProfile(
            listOf(
                rated("c1", 70.0, genres = listOf("Sci-Fi")),
                rated("c2", 70.0, genres = listOf("Sci-Fi"), releaseYear = 2022),
            ),
        )

        val target = candidate("x")
        val lopsided = scoreCandidate(sciFiFan, sciFiSkeptic, target)
        val shared = scoreCandidate(bothLukewarm, bothLukewarm, target)

        assertEquals(
            shared.breakdown.predictedA + shared.breakdown.predictedB,
            lopsided.breakdown.predictedA + lopsided.breakdown.predictedB,
            1e-6,
        )
        assertTrue(shared.finalScore > lopsided.finalScore)
    }

    @Test
    fun `scoreCandidate applies the divergence penalty at the configured rate`() {
        val a = buildTasteProfile(listOf(rated("1", 100.0, genres = listOf("Sci-Fi"))))
        val b = buildTasteProfile(listOf(rated("1", 0.0, genres = listOf("Sci-Fi"))))
        val result = scoreCandidate(a, b, candidate("x"))

        val breakdown = result.breakdown
        assertEquals(abs(breakdown.predictedA - breakdown.predictedB), breakdown.divergence, 1e-6)
        assertEquals(
            (breakdown.predictedA + breakdown.predictedB) / 2 - breakdown.divergence * AlgorithmConfig.DIVERGENCE_PENALTY,
            breakdown.tasteScore,
            1e-6,
        )
    }

    @Test
    fun `scoreCandidate scales the TMDB quality bonus onto the same 0-100 axis as taste`() {
        val profile = buildTasteProfile(listOf(rated("1", 70.0, genres = listOf("Sci-Fi"))))
        val perfect = scoreCandidate(profile, profile, candidate("c1", tmdbRating = 10.0))
        val awful = scoreCandidate(profile, profile, candidate("c2", tmdbRating = 0.0))

        assertEquals(100.0, perfect.breakdown.qualityBonus, 1e-9)
        assertEquals(0.0, awful.breakdown.qualityBonus, 1e-9)
        assertEquals(100.0 * AlgorithmConfig.QUALITY_WEIGHT, perfect.finalScore - awful.finalScore, 1e-6)
    }

    @Test
    fun `scoreCandidate lets shared taste outrank general acclaim, as the low quality weight intends`() {
        val loversOfHorror = buildTasteProfile(
            listOf(
                rated("1", 95.0, genres = listOf("Horror")),
                rated("2", 95.0, genres = listOf("Horror"), releaseYear = 2022),
            ),
        )
        val belovedButWrongGenre = candidate("acclaimed", genres = listOf("Musical"), tmdbRating = 9.5)
        val ourKindOfFilm = candidate("ours", genres = listOf("Horror"), tmdbRating = 6.0)

        val acclaimed = scoreCandidate(loversOfHorror, loversOfHorror, belovedButWrongGenre)
        val ours = scoreCandidate(loversOfHorror, loversOfHorror, ourKindOfFilm)

        assertTrue(ours.finalScore > acclaimed.finalScore)
    }

    @Test
    fun `scoreCandidate never returns a score outside 0-100`() {
        val hater = buildTasteProfile(listOf(rated("1", 0.0, genres = listOf("Sci-Fi"))))
        val lover = buildTasteProfile(listOf(rated("1", 100.0, genres = listOf("Sci-Fi"))))

        for ((a, b) in listOf(hater to hater, lover to lover, hater to lover)) {
            for (tmdbRating in listOf(0.0, 5.0, 10.0)) {
                val finalScore = scoreCandidate(a, b, candidate("x", tmdbRating = tmdbRating)).finalScore
                assertTrue(finalScore >= 0.0)
                assertTrue(finalScore <= 100.0)
            }
        }
    }

    // ---------- buildReason ----------

    @Test
    fun `buildReason names the shared genre when affinity clears the strong threshold`() {
        val fans = buildTasteProfile(
            listOf(
                rated("1", 90.0, genres = listOf("Comedy")),
                rated("2", 90.0, genres = listOf("Comedy"), releaseYear = 2022),
            ),
        )
        val reason = buildReason(fans, fans, candidate("x", genres = listOf("Comedy")))
        assertEquals("You both love Comedy", reason)
    }

    @Test
    fun `buildReason falls back to era when no genre clears the threshold`() {
        val fans = buildTasteProfile(
            listOf(
                rated("1", 90.0, genres = listOf("Drama"), releaseYear = 1985),
                rated("2", 90.0, genres = listOf("Drama"), releaseYear = 1988),
            ),
        )
        val reason = buildReason(fans, fans, candidate("x", genres = listOf("Western"), releaseYear = 1987))
        assertEquals("You both gravitate toward pre-2000s films", reason)
    }

    @Test
    fun `buildReason gives a generic reason when nothing clears either threshold`() {
        val neutral = buildTasteProfile(emptyList())
        val reason = buildReason(neutral, neutral, candidate("x"))
        assertEquals("A pick that fits both your tastes", reason)
    }

    // ---------- rankCandidates ----------

    @Test
    fun `rankCandidates sorts by finalScore descending`() {
        val fans = buildTasteProfile(
            listOf(
                rated("1", 90.0, genres = listOf("Sci-Fi")),
                rated("2", 88.0, genres = listOf("Sci-Fi"), releaseYear = 2022),
            ),
        )
        val result = rankCandidates(
            fans,
            fans,
            listOf(
                candidate("weak", genres = listOf("Musical"), tmdbRating = 4.0),
                candidate("strong", genres = listOf("Sci-Fi"), tmdbRating = 8.0),
                candidate("middling", genres = listOf("Sci-Fi"), tmdbRating = 5.0),
            ),
        )

        assertEquals(listOf("strong", "middling", "weak"), result.ranked.map { it.film.filmId })
        assertFalse(result.noMatches)
    }

    @Test
    fun `rankCandidates reports noMatches instead of suggesting a weak film`() {
        val mismatched = buildTasteProfile(
            listOf(
                rated("1", 5.0, genres = listOf("Musical")),
                rated("2", 2.0, genres = listOf("Musical"), releaseYear = 2022),
            ),
        )
        val result = rankCandidates(mismatched, mismatched, listOf(candidate("bad", genres = listOf("Musical"), tmdbRating = 2.0)))

        assertTrue(result.noMatches)
        assertTrue(result.ranked[0].finalScore < AlgorithmConfig.NO_MATCH_THRESHOLD)
    }

    @Test
    fun `rankCandidates reports noMatches for an empty candidate pool rather than throwing`() {
        val fans = buildTasteProfile(listOf(rated("1", 90.0, genres = listOf("Sci-Fi"))))
        val result = rankCandidates(fans, fans, emptyList())
        assertTrue(result.noMatches)
        assertTrue(result.ranked.isEmpty())
    }

    // ---------- advanceStreak ----------

    private fun at(iso: String): Instant = Instant.parse(iso)

    @Test
    fun `advanceStreak starts at 1 on the pair's first watch`() {
        val result = advanceStreak(StreakState(count = 0, lastWatchAt = null), at("2026-09-03T20:00:00Z"))
        assertEquals(StreakResult(count = 1, changed = true, reason = "first_watch"), result)
    }

    @Test
    fun `advanceStreak continues on the next day`() {
        val state = StreakState(count = 4, lastWatchAt = at("2026-09-02T20:00:00Z"))
        val result = advanceStreak(state, at("2026-09-03T20:00:00Z"))
        assertEquals(5, result.count)
        assertEquals("continued", result.reason)
    }

    @Test
    fun `advanceStreak counts calendar days, not elapsed hours`() {
        // 23:00 on the 2nd to 20:00 on the 3rd is only 21 hours, but two days.
        val state = StreakState(count = 2, lastWatchAt = at("2026-09-02T23:00:00Z"))
        val result = advanceStreak(state, at("2026-09-03T20:00:00Z"))
        assertEquals(3, result.count)
        assertEquals("continued", result.reason)
    }

    @Test
    fun `advanceStreak does not count a second film on the same day`() {
        val state = StreakState(count = 3, lastWatchAt = at("2026-09-03T19:00:00Z"))
        val result = advanceStreak(state, at("2026-09-03T22:30:00Z"))
        assertEquals(StreakResult(count = 3, changed = false, reason = "same_day"), result)
    }

    @Test
    fun `advanceStreak survives a gap inside the grace window`() {
        val state = StreakState(count = 6, lastWatchAt = at("2026-09-03T20:00:00Z"))
        val result = advanceStreak(state, at("2026-09-08T20:00:00Z"))
        assertEquals(7, result.count)
        assertEquals("continued", result.reason)
    }

    @Test
    fun `advanceStreak resets once the gap exceeds the grace window`() {
        val state = StreakState(count = 12, lastWatchAt = at("2026-09-03T20:00:00Z"))
        val result = advanceStreak(state, at("2026-09-14T20:00:00Z"))
        assertEquals(StreakResult(count = 1, changed = true, reason = "reset"), result)
    }

    @Test
    fun `advanceStreak treats exactly the grace window as still alive`() {
        val state = StreakState(count = 2, lastWatchAt = at("2026-09-03T20:00:00Z"))
        assertEquals("continued", advanceStreak(state, at("2026-09-10T20:00:00Z")).reason)
        assertEquals("reset", advanceStreak(state, at("2026-09-11T20:00:00Z")).reason)
    }

    @Test
    fun `advanceStreak resets rather than growing if the clock moves backwards`() {
        val state = StreakState(count = 5, lastWatchAt = at("2026-09-10T20:00:00Z"))
        val result = advanceStreak(state, at("2026-09-03T20:00:00Z"))
        assertEquals(StreakResult(count = 1, changed = true, reason = "reset"), result)
    }

    @Test
    fun `advanceStreak honours a stricter grace window when configured`() {
        val state = StreakState(count = 3, lastWatchAt = at("2026-09-03T20:00:00Z"))
        assertEquals("continued", advanceStreak(state, at("2026-09-04T20:00:00Z"), streakGraceDays = 1).reason)
        assertEquals("reset", advanceStreak(state, at("2026-09-05T20:00:00Z"), streakGraceDays = 1).reason)
    }

    @Test
    fun `advanceStreak pins the documented deviation - UTC calendar days, no per-pair timezone`() {
        // The server's timezone-aware equivalent returns "continued" for a
        // Tokyo-timezoned pair (already the next day there) for this exact
        // instant, but "same_day" for UTC. This port has no timezone
        // parameter, so it always behaves like the UTC case.
        val state = StreakState(count = 1, lastWatchAt = at("2026-09-03T14:00:00Z"))
        val result = advanceStreak(state, at("2026-09-03T23:30:00Z"))
        assertEquals("same_day", result.reason)
    }
}
