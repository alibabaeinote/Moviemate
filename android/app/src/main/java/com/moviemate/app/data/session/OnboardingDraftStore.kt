package com.moviemate.app.data.session

import android.content.Context
import android.content.SharedPreferences
import com.moviemate.app.data.repository.PairRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * Holds the onboarding Taste Dial scores until there is a pair to write them to.
 *
 * This exists because of a hard ordering constraint, not a preference. Ratings
 * live at `pairs/{pairId}/ratings`, so they need a pair. But `createPair` sets
 * `users/{uid}.pairId` the moment it runs, and `joinPair` refuses anyone who
 * already has one — so the app cannot quietly create a pair at sign-up and
 * still let that person join their partner's code later. They would be sealed
 * into an empty pair of their own.
 *
 * The product order (rate first, invite second — show value before asking for
 * commitment) therefore requires buffering: collect the scores, then flush them
 * once the user picks invite or join.
 *
 * Backed by SharedPreferences rather than held in memory because ten films is
 * several minutes of a person's attention, and Android will kill a backgrounded
 * process without warning. Losing that to a phone call is not acceptable.
 */
class OnboardingDraftStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class DraftRating(val filmId: String, val score: Double)

    /** Scores recorded so far, oldest first. */
    fun ratings(): List<DraftRating> {
        val raw = prefs.getString(KEY_RATINGS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                DraftRating(item.getString("filmId"), item.getDouble("score"))
            }
        }.getOrDefault(emptyList())
    }

    /** Records a score, replacing any earlier score for the same film. */
    fun record(filmId: String, score: Double) {
        val updated = ratings().filterNot { it.filmId == filmId } + DraftRating(filmId, score)
        write(updated)
    }

    fun count(): Int = ratings().size

    fun clear() {
        prefs.edit().remove(KEY_RATINGS).apply()
    }

    /**
     * Write the buffered scores into the pair, then clear the buffer.
     *
     * Sequential rather than parallel: each write fires `onRatingComplete`,
     * which recounts the user's ratings and may flip `onboardingComplete` and
     * then `aBothOnboarded`. Ten of those landing at once is ten redundant
     * count queries and a race on the same two documents for no gain — this
     * runs once per user, ever.
     *
     * The buffer is cleared only on full success. A partial flush leaves it
     * intact so a retry re-sends everything; `submitRating` writes to a
     * deterministic document id (`{uid}_{filmId}`), so re-sending overwrites
     * rather than duplicating.
     */
    suspend fun flush(
        pairRepository: PairRepository,
        pairId: String,
        uid: String,
    ): Result<Int> = runCatching {
        val pending = ratings()
        pending.forEach { draft ->
            pairRepository.submitRating(
                pairId = pairId,
                uid = uid,
                filmId = draft.filmId,
                score = draft.score,
                isInitialOnboarding = true,
            ).getOrThrow()
        }
        clear()
        pending.size
    }

    private fun write(ratings: List<DraftRating>) {
        val array = JSONArray()
        ratings.forEach { draft ->
            array.put(JSONObject().put("filmId", draft.filmId).put("score", draft.score))
        }
        prefs.edit().putString(KEY_RATINGS, array.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "moviemate_onboarding_draft"
        const val KEY_RATINGS = "ratings"
    }
}
