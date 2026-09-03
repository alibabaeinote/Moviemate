package com.moviemate.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.moviemate.app.data.model.Match
import com.moviemate.app.data.model.Pair
import com.moviemate.app.data.model.Rating
import com.moviemate.app.data.model.User
import com.moviemate.app.data.model.WatchlistItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.TimeZone

/**
 * Pair, rating, match and watchlist access.
 *
 * Anything that has to be consistent across both users — creating or joining a
 * pair, rejecting a match, scheduling a watch — goes through a callable rather
 * than a direct write, because the security rules close those paths on purpose.
 */
class PairRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("europe-west1"),
) {
    private fun pairDoc(pairId: String) = firestore.collection("pairs").document(pairId)

    // ---------- Pairing ----------

    suspend fun createPair(): Result<InviteInfo> = runCatching {
        val response = functions
            .getHttpsCallable("createPair")
            .call(mapOf("timezone" to TimeZone.getDefault().id))
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = response.data as Map<String, Any?>
        InviteInfo(
            pairId = data["pairId"] as String,
            inviteCode = data["inviteCode"] as String,
            expiresAtMillis = (data["inviteCodeExpiresAt"] as Number).toLong(),
        )
    }

    suspend fun joinPair(inviteCode: String): Result<String> = runCatching {
        val response = functions
            .getHttpsCallable("joinPair")
            .call(mapOf("inviteCode" to inviteCode, "timezone" to TimeZone.getDefault().id))
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = response.data as Map<String, Any?>
        data["pairId"] as String
    }

    // ---------- Onboarding content ----------

    /** Stage 1 of onboarding: the genres the user picks from. */
    suspend fun listGenres(): Result<List<TmdbGenre>> = runCatching {
        val response = functions.getHttpsCallable("listGenres").call().await()

        @Suppress("UNCHECKED_CAST")
        val data = response.data as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val genres = data["genres"] as List<Map<String, Any?>>
        genres.map { TmdbGenre(id = (it["id"] as Number).toInt(), name = it["name"] as String) }
    }

    /**
     * Stage 2: the rating deck, spread across eras rather than just the most
     * popular titles — era and country carry 40% of the scoring weight, and a
     * deck of recent blockbusters teaches the profile neither.
     */
    suspend fun getOnboardingFilms(genreIds: List<Int>): Result<List<DeckFilm>> = runCatching {
        val response = functions
            .getHttpsCallable("getOnboardingFilms")
            .call(mapOf("genreIds" to genreIds))
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = response.data as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val films = data["films"] as List<Map<String, Any?>>
        films.map { film ->
            @Suppress("UNCHECKED_CAST")
            DeckFilm(
                filmId = film["filmId"] as String,
                title = film["title"] as String,
                posterPath = film["posterPath"] as String?,
                genres = (film["genres"] as? List<String>).orEmpty(),
                releaseYear = (film["releaseYear"] as Number).toInt(),
                overview = film["overview"] as? String ?: "",
            )
        }
    }

    // ---------- Live reads ----------

    fun observeUser(uid: String): Flow<User?> = callbackFlow {
        val registration: ListenerRegistration = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.toObject(User::class.java))
            }
        awaitClose { registration.remove() }
    }

    fun observePair(pairId: String): Flow<Pair?> = callbackFlow {
        val registration = pairDoc(pairId).addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.toObject(Pair::class.java))
        }
        awaitClose { registration.remove() }
    }

    /** The current open suggestion, or the most recent one. */
    fun observeCurrentMatch(pairId: String): Flow<Match?> = callbackFlow {
        val registration = pairDoc(pairId).collection("matches")
            .orderBy("suggestedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.documents?.firstOrNull()?.toObject(Match::class.java))
            }
        awaitClose { registration.remove() }
    }

    fun observeWatchlist(pairId: String): Flow<List<WatchlistItem>> = callbackFlow {
        val registration = pairDoc(pairId).collection("watchlist")
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.toObjects(WatchlistItem::class.java) ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    // ---------- Writes the rules allow directly ----------

    /**
     * Write a Taste Dial score.
     *
     * Document id is "{uid}_{filmId}" so re-rating the same film updates rather
     * than creating a duplicate — the documented "Duplicate Rating" case.
     */
    suspend fun submitRating(
        pairId: String,
        uid: String,
        filmId: String,
        score: Double,
        isInitialOnboarding: Boolean,
        reactionEmoji: String? = null,
    ): Result<Unit> = runCatching {
        require(score in 0.0..100.0) { "Taste Dial score must be 0-100" }
        val rating = Rating(
            userId = uid,
            filmId = filmId,
            score = score,
            isInitialOnboarding = isInitialOnboarding,
            reactionEmoji = reactionEmoji,
            ratedAt = Timestamp.now(),
        )
        pairDoc(pairId).collection("ratings").document("${uid}_$filmId").set(rating).await()
    }

    /**
     * "We're in" — flips only the caller's own flag. bothConfirmedAt is stamped
     * server-side once both are true, so neither user can commit for the other.
     */
    suspend fun commitToMatch(
        pairId: String,
        matchId: String,
        isUserA: Boolean,
    ): Result<Unit> = runCatching {
        val field = if (isUserA) "commitStatus.userA" else "commitStatus.userB"
        pairDoc(pairId).collection("matches").document(matchId).update(field, true).await()
    }

    /** Manual "We watched it" — never inferred from a calendar or a streaming service. */
    suspend fun confirmWatched(pairId: String, matchId: String): Result<Unit> = runCatching {
        pairDoc(pairId).collection("matches").document(matchId)
            .update("watchedConfirmedAt", com.google.firebase.firestore.FieldValue.serverTimestamp())
            .await()
    }

    suspend fun rejectMatch(pairId: String, matchId: String): Result<Unit> = runCatching {
        functions.getHttpsCallable("rejectMatch")
            .call(mapOf("pairId" to pairId, "matchId" to matchId))
            .await()
        Unit
    }

    suspend fun scheduleWatch(
        pairId: String,
        matchId: String,
        scheduledForMillis: Long,
    ): Result<Unit> = runCatching {
        functions.getHttpsCallable("scheduleWatch")
            .call(
                mapOf(
                    "pairId" to pairId,
                    "matchId" to matchId,
                    "scheduledForMs" to scheduledForMillis,
                ),
            )
            .await()
        Unit
    }

    suspend fun addToWatchlist(
        pairId: String,
        uid: String,
        filmId: String,
    ): Result<Unit> = runCatching {
        val item = WatchlistItem(
            filmId = filmId,
            addedBy = uid,
            addedAt = Timestamp.now(),
            source = "manual_search",
            status = "waiting",
            watchedAt = null,
            mutualScore = null,
        )
        pairDoc(pairId).collection("watchlist").add(item).await()
        Unit
    }

    /** "I'm in too", straight from the list row (PRD §7.4 item 3). */
    suspend fun commitToWatchlistItem(
        pairId: String,
        itemId: String,
        isUserA: Boolean,
    ): Result<Unit> = runCatching {
        val field = if (isUserA) "commitStatus.userA" else "commitStatus.userB"
        pairDoc(pairId).collection("watchlist").document(itemId).update(field, true).await()
    }
}

data class InviteInfo(
    val pairId: String,
    val inviteCode: String,
    val expiresAtMillis: Long,
)

data class TmdbGenre(
    val id: Int,
    val name: String,
)

/** One card in the onboarding rating deck. */
data class DeckFilm(
    val filmId: String,
    val title: String,
    val posterPath: String?,
    val genres: List<String>,
    val releaseYear: Int,
    val overview: String,
)
