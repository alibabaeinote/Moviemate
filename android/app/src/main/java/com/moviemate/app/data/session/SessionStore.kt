package com.moviemate.app.data.session

import com.moviemate.app.data.model.Pair
import com.moviemate.app.data.model.User
import com.moviemate.app.data.repository.AuthRepository
import com.moviemate.app.data.repository.PairRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Who is signed in, which pair they belong to, and which side of it they are.
 *
 * Every screen past sign-in needs all three, and every one of them would
 * otherwise re-derive it: read the uid from Auth, read `users/{uid}.pairId`,
 * then read the pair to find out whether you are userA or userB. Three
 * listeners per screen instead of one, and `isUserA` computed slightly
 * differently in each place — which matters, because it decides which commit
 * flag a "We're in" tap writes.
 */
data class Session(
    val uid: String,
    val user: User?,
    val pair: Pair?,
) {
    val pairId: String? get() = user?.pairId

    /**
     * Which seat in the pair this user holds. Drives every `commitStatus` write.
     *
     * Defaults to false when the pair has not loaded — a wrong write is worse
     * than no write, and the screens gate their commit buttons on [isPaired].
     */
    val isUserA: Boolean get() = pair != null && pair.userA == uid

    val partnerUid: String? get() = pair?.let { if (it.userA == uid) it.userB else it.userA }

    val isPaired: Boolean get() = pairId != null && pair != null

    /** The partner has taken the second seat — they may still be rating. */
    val partnerJoined: Boolean get() = pair?.userB != null

    /** Server-set: both sides finished onboarding, so daily matches will start. */
    val bothOnboarded: Boolean get() = pair?.aBothOnboarded == true

    val onboardingComplete: Boolean get() = user?.onboardingComplete == true

    val ratingCount: Int get() = user?.ratingCount ?: 0

    /**
     * Everything this session claims to know has actually arrived.
     *
     * A session is emitted as soon as the uid is known, before the user
     * document lands and before the pair behind `pairId` does. Routing on an
     * unsettled session sends a paired user back to the start of onboarding.
     */
    val isSettled: Boolean
        get() = user != null && (user.pairId == null || pair != null)
}

class SessionStore(
    private val authRepository: AuthRepository,
    private val pairRepository: PairRepository,
) {
    /**
     * Null while signed out.
     *
     * flatMapLatest at both levels is the point: signing out has to tear down
     * the user listener, and joining a pair has to start the pair listener
     * without the app being restarted.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val session: Flow<Session?> = authRepository.authState().flatMapLatest { firebaseUser ->
        val uid = firebaseUser?.uid ?: return@flatMapLatest flowOf(null)

        pairRepository.observeUser(uid).flatMapLatest { user ->
            val pairId = user?.pairId
            if (pairId == null) {
                flowOf(Session(uid = uid, user = user, pair = null))
            } else {
                pairRepository.observePair(pairId)
                    .map { pair -> Session(uid = uid, user = user, pair = pair) }
            }
        }
    }
}
