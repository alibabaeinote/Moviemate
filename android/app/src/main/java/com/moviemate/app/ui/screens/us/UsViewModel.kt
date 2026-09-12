package com.moviemate.app.ui.screens.us

import androidx.lifecycle.viewModelScope
import com.moviemate.app.data.model.NotificationSettings
import com.moviemate.app.data.repository.AuthRepository
import com.moviemate.app.data.repository.PairRepository
import com.moviemate.app.data.repository.PairTotals
import com.moviemate.app.data.session.Session
import com.moviemate.app.data.session.SessionStore
import com.moviemate.app.ui.core.MovieMateViewModel
import com.moviemate.app.ui.core.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * What the pair has actually done together.
 *
 * Deliberately three numbers and no badges. Heavy gamification on the shared
 * record was an explicit product rejection (PRD §7.4): the focus belongs on the
 * relationship, not on a score to farm.
 */
data class UsStats(
    val streak: Int,
    /**
     * Films both partners said "We're in" to.
     *
     * PRD §9 is explicit that this — not the number of suggestions — is what a
     * "match" means in any UI count, because the suggestion count only measures
     * how many days the app has been installed.
     */
    val matches: Int,
    val watched: Int,
    val notificationSettings: NotificationSettings,
    val partnerJoined: Boolean,
    val myName: String,
    val myAvatarUrl: String?,
    val partnerName: String?,
    val partnerAvatarUrl: String?,
    /** Picks which of the two fixed partner colors rings each avatar. */
    val isUserA: Boolean,
    /**
     * Watched-together count for the last [JOURNEY_WEEKS] weeks, oldest first.
     * Not a badge — a trend, so an empty recent week reads as "quiet lately"
     * rather than as a broken streak.
     */
    val journeyWeeks: List<Int>,
    /**
     * How closely the two Taste Dials agree, averaged over every film both of
     * them rated. Null until [com.moviemate.app.ui.screens.us.UsStatsMath.MIN_SHARED_RATED_FILMS]
     * shared ratings exist — see that constant for why.
     */
    val compatibilityPercent: Int?,
)

private const val JOURNEY_WEEKS = 6

class UsViewModel(
    private val pairRepository: PairRepository,
    private val authRepository: AuthRepository,
    sessionStore: SessionStore,
) : MovieMateViewModel() {

    private val _state = MutableStateFlow<UiState<UsStats>>(UiState.Loading)
    val state: StateFlow<UiState<UsStats>> = _state.asStateFlow()

    private var session: Session? = null

    /**
     * Declared before [init] on purpose: viewModelScope dispatches with
     * Main.immediate, so a coroutine launched there can run before construction
     * finishes and read a property whose initializer has not executed yet.
     */
    private var totals = PairTotals()
    private var extended = ExtendedStats()

    init {
        viewModelScope.launch {
            sessionStore.session.collect { current ->
                session = current
                render(current)
            }
        }

        // Totals are aggregate queries, so they are refreshed when the pair's
        // own counters move rather than on every session emission — an FCM
        // token write should not cost two more billed counts.
        viewModelScope.launch {
            sessionStore.session
                .map { it?.pair?.streakCount to it?.pair?.lastWatchAt }
                .distinctUntilChanged()
                .collect { refreshTotals() }
        }

        // Journey and compatibility read the raw watchlist/ratings collections,
        // so they are refreshed on the same two signals that can move either
        // one: a new watch, or a new rating.
        viewModelScope.launch {
            sessionStore.session
                .map { Triple(it?.pair?.lastWatchAt, it?.ratingCount, it?.pairId) }
                .distinctUntilChanged()
                .collect { refreshExtended() }
        }
    }

    private suspend fun refreshTotals() {
        val pairId = session?.pairId ?: return
        totals = pairRepository.pairTotals(pairId)
        render(session)
    }

    private suspend fun refreshExtended() {
        val current = session ?: return
        val pairId = current.pairId ?: return
        val userA = current.pair?.userA ?: return
        val userB = current.pair?.userB ?: return

        val inputs = pairRepository.statsInputs(pairId)
        extended = ExtendedStats(
            journeyWeeks = UsStatsMath.weeklyJourney(
                watchedAtMillis = inputs.watchedAtMillis,
                weeks = JOURNEY_WEEKS,
                nowMillis = System.currentTimeMillis(),
            ),
            compatibilityPercent = UsStatsMath.tasteCompatibility(
                ratings = inputs.ratings.map {
                    UsStatsMath.RatingPoint(filmId = it.filmId, userId = it.userId, score = it.score)
                },
                userA = userA,
                userB = userB,
            ),
        )
        render(session)
    }

    private fun render(current: Session?) {
        if (current == null || !current.isPaired) {
            _state.value = UiState.Empty(
                headline = "Nothing to show yet",
                detail = "Your shared record starts once your partner joins.",
            )
            return
        }

        _state.value = UiState.Content(
            UsStats(
                streak = current.pair?.streakCount ?: 0,
                matches = totals.matches,
                watched = totals.watched,
                notificationSettings = current.user?.notificationSettings
                    ?: NotificationSettings(),
                partnerJoined = current.partnerJoined,
                myName = current.displayName ?: "You",
                myAvatarUrl = current.avatarUrl,
                partnerName = current.partnerName,
                partnerAvatarUrl = current.partnerAvatarUrl,
                isUserA = current.isUserA,
                journeyWeeks = extended.journeyWeeks,
                compatibilityPercent = extended.compatibilityPercent,
            ),
        )
    }

    private data class ExtendedStats(
        val journeyWeeks: List<Int> = List(JOURNEY_WEEKS) { 0 },
        val compatibilityPercent: Int? = null,
    )

    /**
     * Notification preferences.
     *
     * One of the six fields the rules let a user write on their own document —
     * everything else there is owned by Cloud Functions.
     */
    fun updateNotifications(settings: NotificationSettings) {
        val uid = session?.uid ?: return
        runAction { authRepository.updateNotificationSettings(uid, settings) }
    }

    fun signOut() = authRepository.signOut()
}
