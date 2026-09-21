package com.moviemate.app.data.recommendation

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Pure recommendation-engine logic — the same port already made once for
 * the web client (web/match-engine.js), from functions/src/domain/
 * {tasteProfile,scoring,reason,streak}.ts. One small file rather than one
 * class per concept, same as the JS version: none of this has state or a
 * reason to be anything but top-level functions over plain data.
 *
 * Deliberate deviation from the server version, same as the web port: no
 * country signal. TMDB's /discover/movie never returns
 * production_countries (only /movie/{id} does), and fetching that per
 * candidate — up to CANDIDATE_POOL_SIZE of them — is too many extra
 * requests for a client build to make eagerly. Its weight folds into
 * genre/era instead.
 */
object AlgorithmConfig {
    const val GENRE_WEIGHT = 0.7
    const val ERA_WEIGHT = 0.3
    const val DIVERGENCE_PENALTY = 0.4
    const val TASTE_WEIGHT = 0.85
    const val QUALITY_WEIGHT = 0.15
    const val NO_MATCH_THRESHOLD = 40.0
    const val NEUTRAL_AFFINITY = 50.0
    const val STRONG_GENRE_THRESHOLD = 70.0
    const val STRONG_ERA_THRESHOLD = 70.0
    const val ONBOARDING_RATING_TARGET = 10

    // Smaller than the server's 200 — these come from live TMDB discover
    // calls made from the device, not a pre-warmed Firestore cache, so
    // every extra candidate is a real extra request.
    const val CANDIDATE_POOL_SIZE = 60
    const val STREAK_GRACE_DAYS = 7
}

data class RatedFilm(
    val filmId: String,
    val genres: List<String>,
    val releaseYear: Int,
    val score: Double,
)

data class ScorableFilm(
    val filmId: String,
    val genres: List<String>,
    val releaseYear: Int,
    val tmdbRating: Double,
)

data class TasteProfile(
    val genreAffinity: Map<String, Double>,
    val eraAffinity: Map<String, Double>,
    val sampleSize: Int,
)

/** Bucket a release year the same way the server does: pre-2000 collapses to one bucket. */
fun decadeKey(releaseYear: Int): String =
    if (releaseYear < 2000) "pre-2000" else "${(releaseYear / 10) * 10}s"

private class Accumulator {
    var total = 0.0
    var weight = 0.0
}

private fun MutableMap<String, Accumulator>.accumulate(key: String, score: Double, weight: Double) {
    val bucket = getOrPut(key) { Accumulator() }
    bucket.total += score * weight
    bucket.weight += weight
}

private fun Map<String, Accumulator>.finalizeAffinity(): Map<String, Double> =
    filterValues { it.weight > 0 }.mapValues { (_, bucket) -> bucket.total / bucket.weight }

/** Build a taste profile from a user's ratings. Every rating currently carries weight 1. */
fun buildTasteProfile(ratedFilms: List<RatedFilm>): TasteProfile {
    val genres = mutableMapOf<String, Accumulator>()
    val eras = mutableMapOf<String, Accumulator>()

    for (film in ratedFilms) {
        for (genre in film.genres) genres.accumulate(genre, film.score, 1.0)
        eras.accumulate(decadeKey(film.releaseYear), film.score, 1.0)
    }

    return TasteProfile(
        genreAffinity = genres.finalizeAffinity(),
        eraAffinity = eras.finalizeAffinity(),
        sampleSize = ratedFilms.size,
    )
}

private fun meanAffinity(affinity: Map<String, Double>, keys: List<String>, neutral: Double): Double {
    val known = keys.mapNotNull { affinity[it] }
    return if (known.isEmpty()) neutral else known.average()
}

/** Predict how much one user would enjoy a candidate film, on the Taste Dial's 0-100 scale. */
fun predictScore(profile: TasteProfile, film: ScorableFilm): Double {
    val genreScore = meanAffinity(profile.genreAffinity, film.genres, AlgorithmConfig.NEUTRAL_AFFINITY)
    val eraScore = meanAffinity(
        profile.eraAffinity,
        listOf(decadeKey(film.releaseYear)),
        AlgorithmConfig.NEUTRAL_AFFINITY,
    )
    return AlgorithmConfig.GENRE_WEIGHT * genreScore + AlgorithmConfig.ERA_WEIGHT * eraScore
}

private fun sharedAffinity(a: Map<String, Double>, b: Map<String, Double>, key: String): Double? {
    val scoreA = a[key] ?: return null
    val scoreB = b[key] ?: return null
    return minOf(scoreA, scoreB)
}

private fun readableDecade(key: String): String = if (key == "pre-2000") "pre-2000s" else key

/** The one-line "why this film" shown under the match card. */
fun buildReason(profileA: TasteProfile, profileB: TasteProfile, film: ScorableFilm): String {
    val genreHits = film.genres
        .mapNotNull { genre ->
            sharedAffinity(profileA.genreAffinity, profileB.genreAffinity, genre)?.let { genre to it }
        }
        .sortedByDescending { it.second }

    val best = genreHits.firstOrNull()
    if (best != null && best.second > AlgorithmConfig.STRONG_GENRE_THRESHOLD) {
        val secondary = genreHits.getOrNull(1)
        return if (secondary != null) {
            "You both love ${best.first} with a ${secondary.first.lowercase()} streak"
        } else {
            "You both love ${best.first}"
        }
    }

    val era = decadeKey(film.releaseYear)
    val sharedEra = sharedAffinity(profileA.eraAffinity, profileB.eraAffinity, era)
    if (sharedEra != null && sharedEra > AlgorithmConfig.STRONG_ERA_THRESHOLD) {
        return "You both gravitate toward ${readableDecade(era)} films"
    }

    return "A pick that fits both your tastes"
}

/** Kept for debugging and for tuning the weights against real data, same as the server's scoring.ts. */
data class ScoreBreakdown(
    val predictedA: Double,
    val predictedB: Double,
    val divergence: Double,
    val tasteScore: Double,
    val qualityBonus: Double,
)

data class ScoredCandidate(
    val film: ScorableFilm,
    val finalScore: Double,
    val reason: String,
    val breakdown: ScoreBreakdown,
)

/**
 * Combine two individual predictions into a joint match score. The
 * divergence penalty is the heart of this: a film predicted 95 for one
 * person and 40 for the other averages to a respectable 67.5, but it is
 * not a *shared* pick — it is one person's film. Penalising the gap
 * corrects that.
 */
fun scoreCandidate(profileA: TasteProfile, profileB: TasteProfile, film: ScorableFilm): ScoredCandidate {
    val predictedA = predictScore(profileA, film)
    val predictedB = predictScore(profileB, film)

    val avgScore = (predictedA + predictedB) / 2
    val divergence = abs(predictedA - predictedB)
    val tasteScore = avgScore - divergence * AlgorithmConfig.DIVERGENCE_PENALTY

    // TMDB's 0-10 vote average, rescaled to the same 0-100 axis. Weighted
    // small on purpose: general acclaim must not outrank these two people's
    // own taste.
    val qualityBonus = (film.tmdbRating / 10.0) * 100.0
    val finalScore = tasteScore * AlgorithmConfig.TASTE_WEIGHT + qualityBonus * AlgorithmConfig.QUALITY_WEIGHT

    return ScoredCandidate(
        film = film,
        finalScore = finalScore.coerceIn(0.0, 100.0),
        reason = buildReason(profileA, profileB, film),
        breakdown = ScoreBreakdown(predictedA, predictedB, divergence, tasteScore, qualityBonus),
    )
}

data class RankingResult(
    val ranked: List<ScoredCandidate>,
    /** True when nothing cleared NO_MATCH_THRESHOLD — the "no matches today" state. */
    val noMatches: Boolean,
)

/**
 * Rank a candidate pool and decide whether there is anything worth
 * suggesting. Returning noMatches rather than the least-bad film is
 * intentional: showing a weak pick costs more trust than showing none.
 */
fun rankCandidates(profileA: TasteProfile, profileB: TasteProfile, candidates: List<ScorableFilm>): RankingResult {
    val ranked = candidates
        .map { scoreCandidate(profileA, profileB, it) }
        .sortedByDescending { it.finalScore }

    val top = ranked.firstOrNull()
    val noMatches = top == null || top.finalScore < AlgorithmConfig.NO_MATCH_THRESHOLD
    return RankingResult(ranked, noMatches)
}

// ---------- Streak ----------

data class StreakState(val count: Int, val lastWatchAt: Instant?)

data class StreakResult(val count: Int, val changed: Boolean, val reason: String)

private fun localDayKey(instant: Instant): String = instant.atOffset(ZoneOffset.UTC).toLocalDate().toString()

/**
 * Advance (or reset) a streak for a watch completed at [watchedAt]. Mirrors
 * domain/streak.ts's pure logic exactly, minus the timezone parameter —
 * the same documented deviation as the web port (web/match-engine.js):
 * this keys off UTC calendar days rather than the pair's stored timezone,
 * since there's no scheduled function on this path to evaluate that
 * per-pair, hourly.
 */
fun advanceStreak(
    state: StreakState,
    watchedAt: Instant,
    streakGraceDays: Int = AlgorithmConfig.STREAK_GRACE_DAYS,
): StreakResult {
    val lastWatchAt = state.lastWatchAt ?: return StreakResult(count = 1, changed = true, reason = "first_watch")

    val previousDay = localDayKey(lastWatchAt)
    val currentDay = localDayKey(watchedAt)

    if (previousDay == currentDay) {
        return StreakResult(count = state.count, changed = false, reason = "same_day")
    }

    val gapDays = ChronoUnit.DAYS.between(LocalDate.parse(previousDay), LocalDate.parse(currentDay))

    return if (gapDays > 0 && gapDays <= streakGraceDays) {
        StreakResult(count = state.count + 1, changed = true, reason = "continued")
    } else {
        // Lapsed (or the clock moved backwards) — start again at this watch.
        StreakResult(count = 1, changed = true, reason = "reset")
    }
}
