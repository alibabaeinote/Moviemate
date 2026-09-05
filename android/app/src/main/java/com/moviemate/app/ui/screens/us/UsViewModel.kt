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
)

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
    }

    private suspend fun refreshTotals() {
        val pairId = session?.pairId ?: return
        totals = pairRepository.pairTotals(pairId)
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
            ),
        )
    }

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
