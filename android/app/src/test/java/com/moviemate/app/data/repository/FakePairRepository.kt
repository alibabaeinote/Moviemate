package com.moviemate.app.data.repository

import com.moviemate.app.data.model.Match
import com.moviemate.app.data.model.Pair
import com.moviemate.app.data.model.User
import com.moviemate.app.data.model.WatchlistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** One `commitToWatchlistItem` call, recorded for assertions. */
data class CommitWatchlistCall(val pairId: String, val itemId: String, val isUserA: Boolean)

/** One `commitToMatch` call, recorded for assertions. */
data class CommitMatchCall(val pairId: String, val matchId: String, val isUserA: Boolean)

/** One `scheduleWatch` call, recorded for assertions. */
data class ScheduleWatchCall(val pairId: String, val matchId: String, val scheduledForMillis: Long)

/** One `confirmWatched` call, recorded for assertions. */
data class ConfirmWatchedCall(val pairId: String, val matchId: String, val uid: String)

/** One `chooseFallbackFilm` call, recorded for assertions. */
data class ChooseFallbackCall(val pairId: String, val matchId: String, val filmId: String)

/** One `submitRating` call, recorded for assertions. */
data class SubmitRatingCall(
    val pairId: String,
    val uid: String,
    val filmId: String,
    val score: Double,
    val isInitialOnboarding: Boolean,
)

/**
 * An in-memory [PairRepository] for ViewModel tests — no Firestore, no
 * Cloud Functions. `pairTotalsResult`/`statsInputsResult`/etc. are read
 * directly by the fake; the `observe*` flows are backed by per-id
 * [MutableStateFlow]s a test can push new values into.
 */
class FakePairRepository : PairRepository {
    private val userFlows = mutableMapOf<String, MutableStateFlow<User?>>()
    private val pairFlows = mutableMapOf<String, MutableStateFlow<Pair?>>()
    private val watchlistFlows = mutableMapOf<String, MutableStateFlow<List<WatchlistItem>>>()
    private val matchFlows = mutableMapOf<String, MutableStateFlow<Match?>>()

    var pairTotalsResult = PairTotals()
    var statsInputsResult = PairStatsInputs()
    var ratingsForFilmResult: Map<String, Double> = emptyMap()
    var searchFilmsResult: Result<List<DeckFilm>> = Result.success(emptyList())
    var addToWatchlistResult: Result<String> = Result.success("new-item-id")

    val committedWatchlistItems = mutableListOf<CommitWatchlistCall>()
    val deletedWatchlistItems = mutableListOf<String>()
    val addedFilmIds = mutableListOf<String>()
    val committedMatches = mutableListOf<CommitMatchCall>()
    val rejectedMatches = mutableListOf<String>()
    val chosenFallbacks = mutableListOf<ChooseFallbackCall>()
    val scheduledWatches = mutableListOf<ScheduleWatchCall>()
    val confirmedWatched = mutableListOf<ConfirmWatchedCall>()
    val submittedRatings = mutableListOf<SubmitRatingCall>()
    var submitRatingResult: Result<Unit> = Result.success(Unit)
    var createPairResult: Result<InviteInfo> =
        Result.success(InviteInfo(pairId = "p1", inviteCode = "ABC123", expiresAtMillis = 0L))
    var createPairCallCount = 0
    var joinPairResult: Result<String> = Result.success("p1")
    val joinedInviteCodes = mutableListOf<String>()
    var listGenresResult: Result<List<TmdbGenre>> = Result.success(emptyList())
    var getOnboardingFilmsResult: Result<List<DeckFilm>> = Result.success(emptyList())
    val requestedGenreIds = mutableListOf<List<Int>>()

    /** Defaults to "done" so tests that don't care about onboarding don't need to set it. */
    var isBothOnboardedResult = true
    var onboardingRatingCountResult = 10
    var generateTodaysMatchResult: Result<Unit> = Result.success(Unit)
    val generateTodaysMatchCalls = mutableListOf<String>()
    var advancePairStreakResult: Result<Unit> = Result.success(Unit)
    val advancePairStreakCalls = mutableListOf<Long>()
    var confirmWatchedResult: Result<Unit> = Result.success(Unit)

    fun setUser(uid: String, user: User?) {
        userFlows.getOrPut(uid) { MutableStateFlow(null) }.value = user
    }

    fun setPair(pairId: String, pair: Pair?) {
        pairFlows.getOrPut(pairId) { MutableStateFlow(null) }.value = pair
    }

    fun setWatchlist(pairId: String, items: List<WatchlistItem>) {
        watchlistFlows.getOrPut(pairId) { MutableStateFlow(emptyList()) }.value = items
    }

    fun setMatch(pairId: String, match: Match?) {
        matchFlows.getOrPut(pairId) { MutableStateFlow(null) }.value = match
    }

    override suspend fun createPair(): Result<InviteInfo> {
        createPairCallCount++
        return createPairResult
    }

    override suspend fun joinPair(inviteCode: String): Result<String> {
        joinedInviteCodes.add(inviteCode)
        return joinPairResult
    }

    override suspend fun listGenres(): Result<List<TmdbGenre>> = listGenresResult

    override suspend fun getOnboardingFilms(genreIds: List<Int>): Result<List<DeckFilm>> {
        requestedGenreIds.add(genreIds)
        return getOnboardingFilmsResult
    }

    override suspend fun searchFilms(query: String): Result<List<DeckFilm>> = searchFilmsResult

    override suspend fun onboardingRatingCount(pairId: String, uid: String): Int = onboardingRatingCountResult

    override suspend fun isBothOnboarded(pairId: String, pair: Pair): Boolean = isBothOnboardedResult

    override suspend fun generateTodaysMatch(pairId: String, pair: Pair): Result<Unit> {
        generateTodaysMatchCalls.add(pairId)
        return generateTodaysMatchResult
    }

    override suspend fun advancePairStreak(pairId: String, pair: Pair, watchedAtMillis: Long): Result<Unit> {
        advancePairStreakCalls.add(watchedAtMillis)
        return advancePairStreakResult
    }

    override fun observeUser(uid: String): Flow<User?> =
        userFlows.getOrPut(uid) { MutableStateFlow(null) }

    override fun observePair(pairId: String): Flow<Pair?> =
        pairFlows.getOrPut(pairId) { MutableStateFlow(null) }

    override fun observeCurrentMatch(pairId: String): Flow<Match?> =
        matchFlows.getOrPut(pairId) { MutableStateFlow(null) }

    override fun observeWatchlist(pairId: String): Flow<List<WatchlistItem>> =
        watchlistFlows.getOrPut(pairId) { MutableStateFlow(emptyList()) }

    override suspend fun ratingsForFilm(pairId: String, filmId: String): Map<String, Double> =
        ratingsForFilmResult

    override suspend fun pairTotals(pairId: String): PairTotals = pairTotalsResult

    override suspend fun statsInputs(pairId: String): PairStatsInputs = statsInputsResult

    override suspend fun deleteWatchlistItem(pairId: String, itemId: String): Result<Unit> {
        deletedWatchlistItems.add(itemId)
        return Result.success(Unit)
    }

    override suspend fun submitRating(
        pairId: String,
        uid: String,
        filmId: String,
        score: Double,
        isInitialOnboarding: Boolean,
        reactionEmoji: String?,
    ): Result<Unit> {
        submittedRatings.add(SubmitRatingCall(pairId, uid, filmId, score, isInitialOnboarding))
        return submitRatingResult
    }

    override suspend fun commitToMatch(pairId: String, matchId: String, isUserA: Boolean): Result<Unit> {
        committedMatches.add(CommitMatchCall(pairId, matchId, isUserA))
        return Result.success(Unit)
    }

    override suspend fun confirmWatched(pairId: String, matchId: String, uid: String): Result<Unit> {
        confirmedWatched.add(ConfirmWatchedCall(pairId, matchId, uid))
        return confirmWatchedResult
    }

    override suspend fun rejectMatch(pairId: String, matchId: String): Result<Unit> {
        rejectedMatches.add(matchId)
        return Result.success(Unit)
    }

    override suspend fun chooseFallbackFilm(pairId: String, matchId: String, filmId: String): Result<Unit> {
        chosenFallbacks.add(ChooseFallbackCall(pairId, matchId, filmId))
        return Result.success(Unit)
    }

    override suspend fun scheduleWatch(pairId: String, matchId: String, scheduledForMillis: Long): Result<Unit> {
        scheduledWatches.add(ScheduleWatchCall(pairId, matchId, scheduledForMillis))
        return Result.success(Unit)
    }

    override suspend fun addToWatchlist(
        pairId: String,
        uid: String,
        filmId: String,
        isUserA: Boolean,
    ): Result<String> {
        addedFilmIds.add(filmId)
        return addToWatchlistResult
    }

    override suspend fun commitToWatchlistItem(
        pairId: String,
        itemId: String,
        isUserA: Boolean,
    ): Result<Unit> {
        committedWatchlistItems.add(CommitWatchlistCall(pairId, itemId, isUserA))
        return Result.success(Unit)
    }
}
