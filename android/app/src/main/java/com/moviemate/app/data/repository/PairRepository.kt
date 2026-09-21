package com.moviemate.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.moviemate.app.data.model.CommitStatus
import com.moviemate.app.data.model.Match
import com.moviemate.app.data.model.Pair
import com.moviemate.app.data.model.Rating
import com.moviemate.app.data.model.User
import com.moviemate.app.data.model.WatchlistItem
import com.moviemate.app.data.recommendation.AlgorithmConfig
import com.moviemate.app.data.recommendation.RatedFilm
import com.moviemate.app.data.recommendation.ScorableFilm
import com.moviemate.app.data.recommendation.StreakState
import com.moviemate.app.data.recommendation.TasteProfile
import com.moviemate.app.data.recommendation.buildTasteProfile
import com.moviemate.app.data.recommendation.rankCandidates
import com.moviemate.app.data.remote.TmdbClient
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.Date
import java.util.TimeZone
import kotlin.math.roundToInt
import com.moviemate.app.data.recommendation.advanceStreak as computeStreakAdvance

/**
 * Pair, rating, match and watchlist access.
 *
 * createPair/joinPair are Cloud Functions callables in the schema doc, but
 * this project has never had the Blaze plan those require to deploy at all
 * (see firestore.rules deviation d) — FirebasePairRepository below performs
 * the same writes directly instead, gated by claimsOwnPair()/joinsOpenSeat().
 * rejectMatch/chooseFallbackFilm/scheduleWatch remain callable-only and are
 * currently non-functional for the same reason; porting those would need new
 * rules this app doesn't have yet (see README's no-Blaze section).
 *
 * An interface, not just a class, so ViewModel tests can substitute a fake
 * instead of talking to Firestore — see `data.repository.FakePairRepository`
 * in the test source set.
 */
interface PairRepository {
    // ---------- Pairing ----------
    suspend fun createPair(): Result<InviteInfo>
    suspend fun joinPair(inviteCode: String): Result<String>

    // ---------- Onboarding content ----------
    suspend fun listGenres(): Result<List<TmdbGenre>>
    suspend fun getOnboardingFilms(genreIds: List<Int>): Result<List<DeckFilm>>
    suspend fun searchFilms(query: String): Result<List<DeckFilm>>

    // ---------- Live onboarding detection (no-Blaze) ----------
    /** How many onboarding ratings this uid has actually written. */
    suspend fun onboardingRatingCount(pairId: String, uid: String): Int

    /** Both partners done rating, computed live rather than trusting aBothOnboarded. */
    suspend fun isBothOnboarded(pairId: String, pair: Pair): Boolean

    // ---------- Daily match generation (no-Blaze) ----------
    /** "Find tonight's movie" — builds and writes today's match, once per ~20h. */
    suspend fun generateTodaysMatch(pairId: String, pair: Pair): Result<Unit>

    /** Runs after a "we watched it" confirmation to advance (or reset) the streak. */
    suspend fun advancePairStreak(pairId: String, pair: Pair, watchedAtMillis: Long): Result<Unit>

    // ---------- Live reads ----------
    fun observeUser(uid: String): Flow<User?>
    fun observePair(pairId: String): Flow<Pair?>
    fun observeCurrentMatch(pairId: String): Flow<Match?>
    fun observeWatchlist(pairId: String): Flow<List<WatchlistItem>>
    suspend fun ratingsForFilm(pairId: String, filmId: String): Map<String, Double>
    suspend fun pairTotals(pairId: String): PairTotals
    suspend fun statsInputs(pairId: String): PairStatsInputs
    suspend fun deleteWatchlistItem(pairId: String, itemId: String): Result<Unit>

    // ---------- Writes the rules allow directly ----------
    suspend fun submitRating(
        pairId: String,
        uid: String,
        filmId: String,
        score: Double,
        isInitialOnboarding: Boolean,
        reactionEmoji: String? = null,
    ): Result<Unit>

    suspend fun commitToMatch(pairId: String, matchId: String, isUserA: Boolean): Result<Unit>
    suspend fun confirmWatched(pairId: String, matchId: String, uid: String): Result<Unit>
    suspend fun rejectMatch(pairId: String, matchId: String): Result<Unit>
    suspend fun chooseFallbackFilm(pairId: String, matchId: String, filmId: String): Result<Unit>
    suspend fun scheduleWatch(pairId: String, matchId: String, scheduledForMillis: Long): Result<Unit>
    suspend fun addToWatchlist(pairId: String, uid: String, filmId: String, isUserA: Boolean): Result<String>
    suspend fun commitToWatchlistItem(pairId: String, itemId: String, isUserA: Boolean): Result<Unit>
}

/** The real, Firestore/Cloud-Functions-backed [PairRepository]. */
class FirebasePairRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("europe-west1"),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val tmdbClient: TmdbClient = TmdbClient(),
) : PairRepository {
    private fun pairDoc(pairId: String) = firestore.collection("pairs").document(pairId)
    private fun userDoc(uid: String) = firestore.collection("users").document(uid)
    private fun inviteCodeDoc(code: String) = firestore.collection("inviteCodes").document(code)

    /** Lower bound for "this timestamp is set". See pairTotals. */
    private val EPOCH = Timestamp(0, 0)

    /**
     * Direct Firestore equivalent of createPair.ts's Admin-SDK transaction —
     * createPair/joinPair are Cloud Functions, and 2nd-gen functions require
     * the Blaze plan to deploy at all, which this project doesn't have (see
     * firestore.rules deviation d). Mirrors web/app.js's createPairDirect
     * write-for-write, gated the same way by claimsOwnPair()/the pairs
     * create rule.
     *
     * Retries on the (unlikely) invite-code collision: a collision surfaces
     * here as the inviteCodes write being evaluated as an update (the doc
     * already exists) against a rule that never allows update, which fails
     * with PERMISSION_DENIED.
     */
    override suspend fun createPair(): Result<InviteInfo> = runCatching {
        val uid = requireNotNull(auth.currentUser?.uid) { "Not signed in." }
        val timezone = TimeZone.getDefault().id

        repeat(MAX_INVITE_CODE_ATTEMPTS) {
            val inviteCode = generateInviteCode()
            val pairRef = firestore.collection("pairs").document()
            val expiresAt = Timestamp(Date(System.currentTimeMillis() + INVITE_CODE_TTL_MS))

            val batch = firestore.batch()
            batch.set(
                pairRef,
                mapOf(
                    "userA" to uid,
                    "userB" to null,
                    "inviteCode" to inviteCode,
                    "inviteCodeExpiresAt" to expiresAt,
                    "status" to "waiting_partner",
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "aBothOnboarded" to false,
                    "streakCount" to 0,
                    "lastMatchGeneratedAt" to null,
                    "lastWatchAt" to null,
                    "timezone" to timezone,
                ),
            )
            batch.set(inviteCodeDoc(inviteCode), mapOf("pairId" to pairRef.id, "expiresAt" to expiresAt))

            try {
                batch.commit().await()
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) return@repeat
                throw e
            }

            userDoc(uid).update("pairId", pairRef.id).await()
            return@runCatching InviteInfo(
                pairId = pairRef.id,
                inviteCode = inviteCode,
                expiresAtMillis = expiresAt.toDate().time,
            )
        }
        error("Could not allocate an invite code. Try again.")
    }

    /**
     * Resolves the code via /inviteCodes, claims the open seat, then claims
     * this uid's own pairId — the same three facts joinPair's Admin-SDK
     * transaction establishes atomically, as separate client writes gated by
     * joinsOpenSeat()/claimsOwnPair(). Mirrors web/app.js's joinPairDirect.
     * The pre-checks below are for a friendly error message only —
     * joinsOpenSeat() re-checks all of this server-side regardless, so a
     * race with someone else joining first is still safe even though these
     * reads are not.
     */
    override suspend fun joinPair(inviteCode: String): Result<String> = runCatching {
        val uid = requireNotNull(auth.currentUser?.uid) { "Not signed in." }

        val codeSnap = inviteCodeDoc(inviteCode).get().await()
        if (!codeSnap.exists()) error("That code doesn't match any invite.")
        val pairId = requireNotNull(codeSnap.getString("pairId")) { "That code doesn't match any invite." }

        val pairSnap = pairDoc(pairId).get().await()
        if (!pairSnap.exists()) error("That code doesn't match any invite.")
        val pair = requireNotNull(pairSnap.toObject(Pair::class.java))
        if (pair.userA == uid) error("That's your own invite code.")
        if (pair.userB != null) error("This invite has already been used.")
        val expiresAt = pairSnap.getTimestamp("inviteCodeExpiresAt")
        if (expiresAt == null || expiresAt.toDate().time <= System.currentTimeMillis()) {
            error("This invite code has expired.")
        }

        val ownProfile = userDoc(uid).get().await().toObject(User::class.java)
        val name = ownProfile?.name?.takeIf { it.isNotBlank() } ?: auth.currentUser?.displayName.orEmpty()
        val avatarUrl = ownProfile?.avatarUrl ?: auth.currentUser?.photoUrl?.toString()

        try {
            pairDoc(pairId).update(
                mapOf(
                    "userB" to uid,
                    "status" to "both_rating",
                    "joinedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "userBName" to name,
                    "userBAvatarUrl" to avatarUrl,
                ),
            ).await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                error("This invite has already been used.")
            }
            throw e
        }

        userDoc(uid).update("pairId", pairId).await()
        pairId
    }

    // ---------- Onboarding content ----------

    /**
     * Stage 1 of onboarding: the genres the user picks from.
     *
     * listGenres/getOnboardingFilms/searchFilms were Cloud Functions callables
     * (see functions/src/callable); this project has never had Blaze to
     * deploy them with, so [tmdbClient] calls TMDB directly instead — the same
     * no-Blaze fallback as web/tmdb.js, and as [createPair]/[joinPair] above.
     */
    override suspend fun listGenres(): Result<List<TmdbGenre>> = runCatching { tmdbClient.listGenres() }

    /**
     * Stage 2: the rating deck, spread across eras rather than just the most
     * popular titles — era and country carry 40% of the scoring weight, and a
     * deck of recent blockbusters teaches the profile neither.
     *
     * No excludeIds here: [extendDeck][com.moviemate.app.ui.screens.onboarding.OnboardingRateViewModel]
     * re-requests the same genres and dedupes against what's already on
     * screen client-side, same as before this call went direct to TMDB.
     */
    override suspend fun getOnboardingFilms(genreIds: List<Int>): Result<List<DeckFilm>> = runCatching {
        tmdbClient.getOnboardingFilms(genreIds, size = ONBOARDING_DECK_SIZE)
    }

    /**
     * Manual film search for the Watchlist.
     *
     * Used to go through the callable rather than TMDB directly because the
     * result had to land in filmCache for anything to resolve it later —
     * moot now that [FirebaseFilmRepository] falls back to TmdbClient on a
     * cache miss instead of depending on filmCache being populated at all.
     */
    override suspend fun searchFilms(query: String): Result<List<DeckFilm>> = runCatching {
        tmdbClient.searchFilms(query)
    }

    // ---------- Live onboarding detection (no-Blaze) ----------

    /**
     * Live replacement for users.onboardingComplete/pairs.aBothOnboarded —
     * both are only ever set by the Blaze-only onRatingComplete trigger,
     * which has never run on this project. Mirrors web/match.js's
     * onboardingRatingCount/isBothOnboarded: an aggregate count against the
     * ratings this uid has actually written, rather than a stored flag
     * nothing can flip without Blaze.
     */
    override suspend fun onboardingRatingCount(pairId: String, uid: String): Int = runCatching {
        pairDoc(pairId).collection("ratings")
            .whereEqualTo("userId", uid)
            .whereEqualTo("isInitialOnboarding", true)
            .count().get(AggregateSource.SERVER).await().count.toInt()
    }.getOrDefault(0)

    override suspend fun isBothOnboarded(pairId: String, pair: Pair): Boolean {
        val userB = pair.userB ?: return false
        val (countA, countB) = coroutineScope {
            val a = async { onboardingRatingCount(pairId, pair.userA) }
            val b = async { onboardingRatingCount(pairId, userB) }
            a.await() to b.await()
        }
        return countA >= AlgorithmConfig.ONBOARDING_RATING_TARGET && countB >= AlgorithmConfig.ONBOARDING_RATING_TARGET
    }

    // ---------- Daily match generation (no-Blaze) ----------

    /** Rebuild one user's taste profile from their full rating history against TMDB. */
    private suspend fun buildProfileFor(pairId: String, uid: String): TasteProfile {
        val ratings = pairDoc(pairId).collection("ratings")
            .whereEqualTo("userId", uid)
            .get().await()
            .toObjects(Rating::class.java)
        if (ratings.isEmpty()) return buildTasteProfile(emptyList())

        val films = tmdbClient.getFilms(ratings.map { it.filmId })
        val rated = ratings.mapNotNull { rating ->
            films[rating.filmId]?.let { film ->
                RatedFilm(
                    filmId = rating.filmId,
                    genres = film.genres,
                    releaseYear = film.releaseYear,
                    score = rating.score,
                )
            }
        }
        return buildTasteProfile(rated)
    }

    /** Films neither user should be offered again: already rated, listed or matched. */
    private suspend fun excludedFilmIds(pairId: String): Set<String> = coroutineScope {
        val ratings = async { pairDoc(pairId).collection("ratings").get().await() }
        val watchlist = async { pairDoc(pairId).collection("watchlist").get().await() }
        val matches = async { pairDoc(pairId).collection("matches").get().await() }

        buildSet {
            ratings.await().toObjects(Rating::class.java).forEach { add(it.filmId) }
            watchlist.await().toObjects(WatchlistItem::class.java).forEach { add(it.filmId) }
            matches.await().toObjects(Match::class.java).forEach { if (it.filmId.isNotBlank()) add(it.filmId) }
        }
    }

    /**
     * The no-Blaze fallback for generateDailyMatch: builds both taste
     * profiles, pulls a candidate pool from TMDB, ranks it, and writes
     * today's match doc plus the pair's lastMatchGeneratedAt — mirrors
     * web/match.js's generateTodaysMatch write-for-write, including running
     * the profile-building and TMDB fetch outside the transaction (a
     * transaction body should only do Firestore reads/writes) and having the
     * transaction itself re-check the 20h gate against the *current*
     * lastMatchGeneratedAt before writing. If two clients call this within
     * the same window, Firestore's optimistic concurrency retries the
     * loser's transaction against the winner's already-committed state, and
     * the gate check then throws a clean error instead of a duplicate match
     * ever being written.
     */
    override suspend fun generateTodaysMatch(pairId: String, pair: Pair): Result<Unit> = runCatching {
        val userB = requireNotNull(pair.userB) { "No partner yet." }

        val profileA: TasteProfile
        val profileB: TasteProfile
        val excluded: Set<String>
        coroutineScope {
            val a = async { buildProfileFor(pairId, pair.userA) }
            val b = async { buildProfileFor(pairId, userB) }
            val e = async { excludedFilmIds(pairId) }
            profileA = a.await()
            profileB = b.await()
            excluded = e.await()
        }

        val candidates = tmdbClient.getMatchCandidates(AlgorithmConfig.CANDIDATE_POOL_SIZE, excluded)
            .map { ScorableFilm(it.filmId, it.genres, it.releaseYear, it.tmdbRating) }
        val result = rankCandidates(profileA, profileB, candidates)

        val base = mapOf(
            "attemptNumber" to 1,
            "suggestedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "commitStatus" to mapOf("userA" to false, "userB" to false),
            "bothConfirmedAt" to null,
            "watchedConfirmedAt" to null,
            "watchedConfirmedBy" to null,
            "shortlist" to emptyList<Any>(),
        )

        val matchData: Map<String, Any?> = if (result.noMatches) {
            base + mapOf(
                "filmId" to "",
                "score" to 0,
                "reason" to "",
                "status" to "dismissed",
                "noMatchesReason" to if (candidates.isEmpty()) {
                    "We ran out of fresh films to suggest."
                } else {
                    "Nothing scored high enough for both of you today."
                },
            )
        } else {
            val top = result.ranked.first()
            base + mapOf(
                "filmId" to top.film.filmId,
                "score" to top.finalScore.roundToInt(),
                "reason" to top.reason,
                "status" to "suggested",
            )
        }

        val matchRef = pairDoc(pairId).collection("matches").document()
        val pairRef = pairDoc(pairId)

        firestore.runTransaction<Unit> { transaction ->
            val pairSnap = transaction.get(pairRef)
            val lastGenerated = pairSnap.getTimestamp("lastMatchGeneratedAt")
            if (lastGenerated != null &&
                System.currentTimeMillis() - lastGenerated.toDate().time < GENERATION_GATE_MS
            ) {
                error("Today's match has already been found.")
            }
            transaction.set(matchRef, matchData)
            transaction.update(pairRef, "lastMatchGeneratedAt", com.google.firebase.firestore.FieldValue.serverTimestamp())
            Unit
        }.await()
    }

    /**
     * The no-Blaze fallback for onMatchUpdate's updateStreak: run the same
     * pure advanceStreak() logic (data.recommendation.MatchEngine.kt)
     * against the pair's current state and write the result. Called by
     * whichever client just confirmed "we watched it" (confirmsWatchedOnly
     * already allows that write, unchanged).
     */
    override suspend fun advancePairStreak(pairId: String, pair: Pair, watchedAtMillis: Long): Result<Unit> =
        runCatching {
            val watchedAt = Instant.ofEpochMilli(watchedAtMillis)
            val state = StreakState(
                count = pair.streakCount,
                lastWatchAt = pair.lastWatchAt?.toDate()?.toInstant(),
            )
            val result = computeStreakAdvance(state, watchedAt)
            if (!result.changed) return@runCatching

            pairDoc(pairId).update(
                mapOf(
                    "streakCount" to result.count,
                    "lastWatchAt" to Timestamp(Date(watchedAtMillis)),
                ),
            ).await()
        }

    // ---------- Live reads ----------

    override fun observeUser(uid: String): Flow<User?> = callbackFlow {
        val registration: ListenerRegistration = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.toObject(User::class.java))
            }
        awaitClose { registration.remove() }
    }

    override fun observePair(pairId: String): Flow<Pair?> = callbackFlow {
        val registration = pairDoc(pairId).addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.toObject(Pair::class.java))
        }
        awaitClose { registration.remove() }
    }

    /** The current open suggestion, or the most recent one. */
    override fun observeCurrentMatch(pairId: String): Flow<Match?> = callbackFlow {
        val registration = pairDoc(pairId).collection("matches")
            .orderBy("suggestedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.documents?.firstOrNull()?.toObject(Match::class.java))
            }
        awaitClose { registration.remove() }
    }

    override fun observeWatchlist(pairId: String): Flow<List<WatchlistItem>> = callbackFlow {
        val registration = pairDoc(pairId).collection("watchlist")
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.toObjects(WatchlistItem::class.java) ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    /**
     * Both partners' scores for one film, keyed by uid.
     *
     * The Watchlist shows two markers on a single shared axis rather than two
     * separate numbers (PRD §7.4 item 6), which needs the individual scores —
     * the item's stored mutualScore is already collapsed to one figure.
     */
    override suspend fun ratingsForFilm(pairId: String, filmId: String): Map<String, Double> =
        runCatching {
            pairDoc(pairId).collection("ratings")
                .whereEqualTo("filmId", filmId)
                .get().await()
                .toObjects(Rating::class.java)
                .associate { it.userId to it.score }
        }.getOrDefault(emptyMap())

    /**
     * How many matches both partners committed to, and how many they watched.
     *
     * Aggregate queries, not a full read of the collection: this is one billed
     * count per figure instead of one read per match document, and the number
     * grows by one a day forever.
     *
     * "Match" here means both said "We're in" — PRD §9 is explicit that a
     * suggestion nobody confirmed is not a match, because the count of
     * algorithm suggestions only measures how many days the app has been
     * installed.
     *
     * The "is set" test is a range filter rather than `!= null`: Firestore
     * accepts null only with equality, and a range filter also restricts
     * results to the operand's type, so unconfirmed matches — which carry an
     * explicit null — fall outside it.
     */
    override suspend fun pairTotals(pairId: String): PairTotals = runCatching {
        val matches = pairDoc(pairId).collection("matches")
        val confirmed = matches
            .whereGreaterThan("bothConfirmedAt", EPOCH)
            .count().get(AggregateSource.SERVER).await().count
        val watched = matches
            .whereGreaterThan("watchedConfirmedAt", EPOCH)
            .count().get(AggregateSource.SERVER).await().count

        PairTotals(matches = confirmed.toInt(), watched = watched.toInt())
    }.getOrDefault(PairTotals())

    /**
     * Raw inputs for the Us screen's journey and compatibility numbers — see
     * [com.moviemate.app.ui.screens.us.UsStatsMath] for what happens to them.
     *
     * Both `ratings` and `watchlist` are readable by any pair member under the
     * security rules (unlike users/{uid}), so this reads straight from
     * Firestore rather than through a callable, the same way [pairTotals] does.
     */
    override suspend fun statsInputs(pairId: String): PairStatsInputs = runCatching {
        val watchlistSnapshot = pairDoc(pairId).collection("watchlist")
            .whereEqualTo("status", "watched")
            .get().await()
        val watchedAtMillis = watchlistSnapshot.toObjects(WatchlistItem::class.java)
            .mapNotNull { it.watchedAt?.toDate()?.time }

        val ratings = pairDoc(pairId).collection("ratings")
            .get().await()
            .toObjects(Rating::class.java)

        PairStatsInputs(watchedAtMillis = watchedAtMillis, ratings = ratings)
    }.getOrDefault(PairStatsInputs())

    /** Either member may remove a film from the shared list. */
    override suspend fun deleteWatchlistItem(pairId: String, itemId: String): Result<Unit> = runCatching {
        pairDoc(pairId).collection("watchlist").document(itemId).delete().await()
    }

    // ---------- Writes the rules allow directly ----------

    /**
     * Write a Taste Dial score.
     *
     * Document id is "{uid}_{filmId}" so re-rating the same film updates rather
     * than creating a duplicate — the documented "Duplicate Rating" case.
     */
    override suspend fun submitRating(
        pairId: String,
        uid: String,
        filmId: String,
        score: Double,
        isInitialOnboarding: Boolean,
        reactionEmoji: String?,
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
    override suspend fun commitToMatch(
        pairId: String,
        matchId: String,
        isUserA: Boolean,
    ): Result<Unit> = runCatching {
        val field = if (isUserA) "commitStatus.userA" else "commitStatus.userB"
        pairDoc(pairId).collection("matches").document(matchId).update(field, true).await()
    }

    /**
     * Manual "We watched it" — never inferred from a calendar or a streaming
     * service. Both fields are written together: the rules require it, so the
     * server side knows who confirmed and can prompt the OTHER partner to rate.
     */
    override suspend fun confirmWatched(pairId: String, matchId: String, uid: String): Result<Unit> =
        runCatching {
            pairDoc(pairId).collection("matches").document(matchId)
                .update(
                    mapOf(
                        "watchedConfirmedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        "watchedConfirmedBy" to uid,
                    ),
                )
                .await()
        }

    override suspend fun rejectMatch(pairId: String, matchId: String): Result<Unit> = runCatching {
        functions.getHttpsCallable("rejectMatch")
            .call(mapOf("pairId" to pairId, "matchId" to matchId))
            .await()
        Unit
    }

    /**
     * Pick one of the three films on the fallback screen.
     *
     * A callable rather than a direct write because `filmId` is closed to
     * clients on purpose — it is shared state, and either partner rewriting it
     * would change the film out from under the other's commitment.
     */
    override suspend fun chooseFallbackFilm(
        pairId: String,
        matchId: String,
        filmId: String,
    ): Result<Unit> = runCatching {
        functions.getHttpsCallable("chooseFallbackFilm")
            .call(mapOf("pairId" to pairId, "matchId" to matchId, "filmId" to filmId))
            .await()
        Unit
    }

    override suspend fun scheduleWatch(
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

    /**
     * Add a film someone searched for, already committed on their own side.
     *
     * The adder's commit flag is set in the create rather than in a follow-up
     * write: proposing a film to your partner *is* saying you want to watch it,
     * and the rules constrain only status, watchedAt and mutualScore on create.
     * One write instead of two, and no window where the list shows a film
     * nobody appears to want.
     */
    override suspend fun addToWatchlist(
        pairId: String,
        uid: String,
        filmId: String,
        isUserA: Boolean,
    ): Result<String> = runCatching {
        val item = WatchlistItem(
            filmId = filmId,
            addedBy = uid,
            addedAt = Timestamp.now(),
            source = "manual_search",
            status = "waiting",
            // Only the adder's own seat. Written out rather than as a pair of
            // negations, because setting the other side here is precisely the
            // bug commitsOnlyForSelf() exists to stop.
            commitStatus = CommitStatus(
                userA = isUserA,
                userB = !isUserA,
            ),
            watchedAt = null,
            mutualScore = null,
        )
        pairDoc(pairId).collection("watchlist").add(item).await().id
    }

    /** "I'm in too", straight from the list row (PRD §7.4 item 3). */
    override suspend fun commitToWatchlistItem(
        pairId: String,
        itemId: String,
        isUserA: Boolean,
    ): Result<Unit> = runCatching {
        val field = if (isUserA) "commitStatus.userA" else "commitStatus.userB"
        pairDoc(pairId).collection("watchlist").document(itemId).update(field, true).await()
    }

    private companion object {
        // Mirrors web/app.js's generateInviteCode/INVITE_CODE_TTL_MS, itself
        // mirroring functions/src/lib/pairs.ts.
        const val INVITE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no I/O/0/1
        const val INVITE_CODE_LENGTH = 6
        const val INVITE_CODE_TTL_MS = 7L * 24 * 60 * 60 * 1000 // 7 days, ALI-73
        const val MAX_INVITE_CODE_ATTEMPTS = 5

        // Mirrors ui.screens.onboarding.OnboardingConfig.DECK_SIZE (data can't
        // depend on ui, hence the separate constant) and web/app.js's own
        // DECK_SIZE — bigger than the 10-rating target so a narrow-taste user
        // has headroom to skip without hitting the end of the deck.
        const val ONBOARDING_DECK_SIZE = 20

        // Mirrors createsTodaysMatch()'s duration.value(20, 'h') and
        // web/match.js's GENERATION_GATE_MS.
        const val GENERATION_GATE_MS = 20L * 60 * 60 * 1000

        fun generateInviteCode(): String {
            val code = (1..INVITE_CODE_LENGTH).map { INVITE_CODE_ALPHABET.random() }.joinToString("")
            return "MVMT-$code"
        }
    }
}

/** Headline numbers for the Us screen. */
data class PairTotals(
    val matches: Int = 0,
    val watched: Int = 0,
)

/** Raw data [com.moviemate.app.ui.screens.us.UsStatsMath] turns into journey/compatibility. */
data class PairStatsInputs(
    val watchedAtMillis: List<Long> = emptyList(),
    val ratings: List<Rating> = emptyList(),
)

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
    // Unused by the onboarding/search UI, which predates this field — added
    // so the same DeckFilm shape also works as a scoring candidate for the
    // no-Blaze daily match (see data.remote.TmdbClient.getMatchCandidates
    // and data.recommendation.scoreCandidate).
    val tmdbRating: Double = 0.0,
)
