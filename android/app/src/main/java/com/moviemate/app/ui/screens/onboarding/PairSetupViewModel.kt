package com.moviemate.app.ui.screens.onboarding

import androidx.lifecycle.viewModelScope
import com.moviemate.app.data.repository.PairRepository
import com.moviemate.app.data.session.OnboardingDraftStore
import com.moviemate.app.data.session.SessionStore
import com.moviemate.app.ui.core.MovieMateViewModel
import com.moviemate.app.ui.core.UiState
import com.moviemate.app.ui.core.readableMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Creating or joining a pair — the two halves of the same decision.
 *
 * They share a ViewModel because they share the step that matters: whichever
 * path the user takes, the buffered onboarding scores have to be flushed into
 * the pair that now exists, and neither screen is finished until that lands.
 */
class PairSetupViewModel(
    private val pairRepository: PairRepository,
    private val sessionStore: SessionStore,
    private val draftStore: OnboardingDraftStore,
) : MovieMateViewModel() {

    data class Invite(
        val inviteCode: String,
        val expiresAtMillis: Long,
    )

    private val _invite = MutableStateFlow<UiState<Invite>>(UiState.Loading)
    val invite: StateFlow<UiState<Invite>> = _invite.asStateFlow()

    private val _ready = MutableStateFlow(false)

    /** Flips once a pair exists and the buffered scores have been dealt with. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /**
     * Create the pair, or recover the code if one already exists.
     *
     * `createPair` rejects a second call with "You are already paired", which is
     * correct on the server but wrong to show someone who simply rotated their
     * phone. So an existing pair is read back rather than reported as an error.
     */
    fun startInvite() {
        _invite.value = UiState.Loading
        viewModelScope.launch {
            val session = sessionStore.session.firstOrNull()
            val existing = session?.pair

            if (existing != null && existing.inviteCode.isNotBlank()) {
                _invite.value = UiState.Content(
                    Invite(
                        inviteCode = existing.inviteCode,
                        expiresAtMillis = existing.inviteCodeExpiresAt?.toDate()?.time ?: 0L,
                    ),
                )
                flushDraft()
                return@launch
            }

            _invite.value = pairRepository.createPair().fold(
                onSuccess = { info ->
                    flushDraft()
                    UiState.Content(Invite(info.inviteCode, info.expiresAtMillis))
                },
                onFailure = { UiState.Failed(it.readableMessage()) },
            )
        }
    }

    /**
     * Join with a partner's code.
     *
     * Reported through [action] rather than [invite] because a wrong code is a
     * correctable mistake in a form, not a broken screen — the field and the
     * button must stay on screen next to the message.
     */
    fun join(inviteCode: String) {
        runAction(
            onSuccess = { flushDraft() },
        ) {
            pairRepository.joinPair(inviteCode.trim().uppercase())
        }
    }

    /**
     * Write the buffered onboarding scores into the pair.
     *
     * Deliberately not surfaced as a blocking error: the pair exists either way,
     * and the alternative is stranding someone on the invite screen with a
     * working invite code they cannot see. A failed flush leaves the draft
     * intact, and the next entry to this screen retries it.
     */
    private suspend fun flushDraft() {
        // The callable has returned, but this client learns its own pairId from
        // the users/{uid} listener, which lands a moment later. Bounded so a
        // listener that never fires cannot pin the screen open forever.
        val session = withTimeoutOrNull(PAIR_PROPAGATION_TIMEOUT_MS) {
            sessionStore.session.first { it?.pairId != null }
        }
        val pairId = session?.pairId

        if (session != null && pairId != null && draftStore.count() > 0) {
            draftStore.flush(pairRepository, pairId, session.uid)
        }
        _ready.value = true
    }

    private companion object {
        const val PAIR_PROPAGATION_TIMEOUT_MS = 10_000L
    }
}
