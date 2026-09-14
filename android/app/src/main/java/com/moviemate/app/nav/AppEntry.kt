package com.moviemate.app.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moviemate.app.data.session.OnboardingDraftStore
import com.moviemate.app.data.session.Session
import com.moviemate.app.data.session.SessionStore
import com.moviemate.app.ui.screens.onboarding.OnboardingConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where the app opens.
 *
 * [Undecided] is a real state, not a placeholder: Auth and Firestore both
 * answer asynchronously, and a graph built before they do sends a signed-in
 * user to the Welcome screen. Navigating away from that afterwards is visible
 * and feels like a bug, so the graph waits instead.
 */
sealed interface AppEntry {
    data object Undecided : AppEntry
    data class Route(val route: String) : AppEntry
}

class AppEntryViewModel(
    sessionStore: SessionStore,
    private val draftStore: OnboardingDraftStore,
) : ViewModel() {

    private val _entry = MutableStateFlow<AppEntry>(AppEntry.Undecided)
    val entry: StateFlow<AppEntry> = _entry.asStateFlow()

    init {
        viewModelScope.launch {
            val session = withTimeoutOrNull(SETTLE_TIMEOUT_MS) {
                // Wait for a session that can actually be routed on. The first
                // emission after sign-in often has no user document yet, and
                // routing off that sends a paired user back into onboarding.
                sessionStore.session.first { it == null || it.isSettled }
            } ?: sessionStore.session.first()

            // Decided once. After this the NavController owns navigation —
            // recomputing on every session change would yank the user out of
            // whatever screen they are on the moment their partner joins.
            _entry.value = AppEntry.Route(startRouteFor(session, draftStore.count()))
        }
    }

    internal companion object {

        /**
         * Long enough for a cold Firestore read on a slow connection, short
         * enough that a blank screen does not read as a hang.
         */
        const val SETTLE_TIMEOUT_MS = 5_000L

        /**
         * Resume the user where onboarding actually left off.
         *
         * Once this user's own onboarding is done, the destination is always
         * the Match tab, whether or not the server has flipped `bothOnboarded`
         * yet — the Match screen itself shows the right "waiting on partner"
         * copy for as long as that takes (see MatchPhase.WaitingForPartner).
         * There is no separate holding screen to route to instead.
         */
        fun startRouteFor(session: Session?, draftCount: Int): String = when {
            session == null -> Routes.WELCOME

            // Own onboarding done, paired or not yet fully matched — Match
            // itself resolves the rest.
            session.isPaired && session.onboardingComplete -> Routes.MATCH
            session.bothOnboarded -> Routes.MATCH

            // Paired but still rating.
            session.isPaired -> Routes.ONBOARDING_RATE

            // Not paired, but the ten films are already rated and buffered:
            // the only thing left is choosing invite or join.
            draftCount >= OnboardingConfig.RATING_TARGET -> Routes.INVITE_PARTNER

            else -> Routes.ONBOARDING_RATE
        }
    }
}
