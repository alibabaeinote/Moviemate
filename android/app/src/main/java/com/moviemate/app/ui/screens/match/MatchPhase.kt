package com.moviemate.app.ui.screens.match

import com.moviemate.app.data.model.Film
import com.moviemate.app.data.model.Match
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

    /** No match document at all — before the pair's 9am local run. */
    data object NotYet : MatchPhase

    /** A match was generated but nothing cleared the threshold. */
    data class NoMatches(val reason: String) : MatchPhase

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

    /** Watched and closed — the pair's cue to rate it. */
    data class Watched(
        val match: Match,
        val film: Film?,
    ) : MatchPhase
}

/**
 * Read a match document from one user's side.
 *
 * Order matters and runs backwards through the lifecycle: a watched match also
 * has both commit flags set, and a confirmed one also has a film — so the
 * latest state has to be tested first or an earlier branch swallows it.
 */
fun matchPhaseOf(
    match: Match?,
    session: Session,
    film: Film?,
    shortlistFilms: Map<String, Film> = emptyMap(),
): MatchPhase {
    if (match == null) return MatchPhase.NotYet

    if (match.watchedConfirmedAt != null) {
        return MatchPhase.Watched(match, film)
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
            match.noMatchesReason ?: "Nothing scored high enough for both of you today.",
        )
    }

    // Dismissed without the fallback unlocking means the day is simply over.
    if (match.status == "dismissed") {
        return MatchPhase.NoMatches("You passed on today's picks. A fresh one lands tomorrow.")
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
