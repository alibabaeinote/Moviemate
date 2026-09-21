package com.moviemate.app.ui.screens.match

import com.moviemate.app.data.model.Film
import com.moviemate.app.data.model.Match
import com.moviemate.app.data.model.Pair
import com.moviemate.app.data.model.ShortlistEntry
import com.moviemate.app.data.session.Session

/**
 * What today's match currently is, from this user's side.
 *
 * Derived from the match document rather than stored, because the document is
 * the shared truth and both phones must reach the same conclusion from it. The
 * asymmetric cases are the reason this is an enum-like hierarchy and not a
 * boolean: "I'm in, waiting on you" and "they're in, are you?" are the same
 * document read from two sides, and they need different screens.
 */
sealed interface MatchPhase {

    /**
     * No match document at all yet. There's no scheduled function running a
     * 9am pass on this no-Blaze path — "Find tonight's movie" is a client
     * action instead (see PairRepository.generateTodaysMatch).
     */
    data object NotYet : MatchPhase

    /**
     * The pair isn't generating matches yet because onboarding isn't done on
     * both sides. Shown on the Match tab itself — with the bottom nav still
     * visible — rather than a separate holding screen, so Watchlist and Us
     * stay reachable while this resolves.
     *
     * Carries both people's identity so the screen can show who it's actually
     * waiting on rather than a bare headline, and the invite code so a
     * [PartnerWaitStage.NoPartner] user can re-share it without leaving the tab.
     */
    data class WaitingForPartner(
        val stage: PartnerWaitStage,
        val myName: String,
        val myAvatarUrl: String?,
        val partnerName: String?,
        val partnerAvatarUrl: String?,
        val isUserA: Boolean,
        val inviteCode: String?,
    ) : MatchPhase

    /**
     * A match was generated but nothing cleared the threshold, or the pair
     * dismissed it. [canRetry] mirrors createsTodaysMatch()'s 20h gate so the
     * retry button doesn't invite a write the rules will just reject.
     */
    data class NoMatches(val reason: String, val canRetry: Boolean) : MatchPhase

    /** Open suggestion. [attemptNumber] is 1-3 of the one-at-a-time sequence. */
    data class Suggested(
        val match: Match,
        val film: Film?,
        val attemptNumber: Int,
        val iCommitted: Boolean,
        val partnerCommitted: Boolean,
    ) : MatchPhase

    /** All three rejected: the 3-up screen, which is a last resort, not a menu. */
    data class Fallback(
        val match: Match,
        val options: List<ShortlistEntry>,
        val films: Map<String, Film>,
    ) : MatchPhase

    /** Both said yes. Scheduling opens only here (PRD §7.2). */
    data class Confirmed(
        val match: Match,
        val film: Film?,
        val scheduledForMillis: Long?,
    ) : MatchPhase

    /** Watched and closed — the pair's cue to rate it. [canRetry]: see [NoMatches]. */
    data class Watched(
        val match: Match,
        val film: Film?,
        val canRetry: Boolean,
    ) : MatchPhase
}

/**
 * Mirrors createsTodaysMatch()'s 20h gate client-side, purely so the retry
 * button (shown once today's match reaches a terminal state — watched, or no
 * match found) doesn't invite a write the rules will just reject. The rule is
 * still the actual authority; this is UX only.
 */
fun canGenerateAgain(pair: Pair?): Boolean {
    val last = pair?.lastMatchGeneratedAt ?: return true
    return System.currentTimeMillis() - last.toDate().time > GENERATION_GATE_MS_UX
}

// Mirrors createsTodaysMatch()'s duration.value(20, 'h') and
// PairRepository.GENERATION_GATE_MS.
private const val GENERATION_GATE_MS_UX = 20L * 60 * 60 * 1000

/**
 * What the pair is still waiting on before matches can start, in the order it
 * resolves. Two distinct waits, not one: if the partner never joined, the
 * invite code may need re-sending — collapsing that into "they're rating"
 * would hide the one thing this user can act on.
 */
enum class PartnerWaitStage { NoPartner, PartnerRating }

/**
 * Read a match document from one user's side.
 *
 * Order matters and runs backwards through the lifecycle: a watched match also
 * has both commit flags set, and a confirmed one also has a film — so the
 * latest state has to be tested first or an earlier branch swallows it.
 *
 * `bothOnboarded` is checked before the match document is: a pair mid-onboarding
 * has no match yet for a completely different reason than "it isn't 9am yet",
 * and the two need different copy.
 *
 * [bothOnboarded] is passed in rather than read off `session.pair.aBothOnboarded`
 * — that field is only ever set by the Blaze-only onRatingComplete trigger,
 * which has never run on this project (see PairRepository.isBothOnboarded's
 * doc comment), so it would never actually flip. The caller is expected to
 * have computed it live instead (see MatchViewModel).
 */
fun matchPhaseOf(
    match: Match?,
    session: Session,
    film: Film?,
    bothOnboarded: Boolean,
    shortlistFilms: Map<String, Film> = emptyMap(),
): MatchPhase {
    if (!bothOnboarded) {
        val stage = if (session.partnerJoined) {
            PartnerWaitStage.PartnerRating
        } else {
            PartnerWaitStage.NoPartner
        }
        return MatchPhase.WaitingForPartner(
            stage = stage,
            myName = session.displayName ?: "You",
            myAvatarUrl = session.avatarUrl,
            partnerName = session.partnerName,
            partnerAvatarUrl = session.partnerAvatarUrl,
            isUserA = session.isUserA,
            inviteCode = session.pair?.inviteCode,
        )
    }

    if (match == null) return MatchPhase.NotYet

    if (match.watchedConfirmedAt != null) {
        return MatchPhase.Watched(match, film, canRetry = canGenerateAgain(session.pair))
    }

    if (match.bothConfirmedAt != null) {
        return MatchPhase.Confirmed(
            match = match,
            film = film,
            scheduledForMillis = match.scheduledFor?.toDate()?.time,
        )
    }

    if (match.fallbackUnlocked) {
        return MatchPhase.Fallback(match, match.shortlist, shortlistFilms)
    }

    // A no-match day is written as a real document with an empty filmId, so the
    // client has an unambiguous state instead of an empty collection it would
    // have to guess about.
    if (match.filmId.isBlank()) {
        return MatchPhase.NoMatches(
            reason = match.noMatchesReason ?: "Nothing scored high enough for both of you today.",
            canRetry = canGenerateAgain(session.pair),
        )
    }

    // Dismissed without the fallback unlocking means the day is simply over.
    if (match.status == "dismissed") {
        return MatchPhase.NoMatches(
            reason = "You passed on today's picks. A fresh one lands tomorrow.",
            canRetry = canGenerateAgain(session.pair),
        )
    }

    return MatchPhase.Suggested(
        match = match,
        film = film,
        attemptNumber = match.attemptNumber,
        iCommitted = if (session.isUserA) match.commitStatus.userA else match.commitStatus.userB,
        partnerCommitted =
            if (session.isUserA) match.commitStatus.userB else match.commitStatus.userA,
    )
}
