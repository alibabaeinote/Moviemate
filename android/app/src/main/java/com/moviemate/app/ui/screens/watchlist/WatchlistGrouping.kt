package com.moviemate.app.ui.screens.watchlist

import com.moviemate.app.data.model.Film
import com.moviemate.app.data.model.WatchlistItem
import com.moviemate.app.data.session.Session

/**
 * Which of the three sections an item belongs in, from this user's side.
 *
 * The stored status has three values but the middle one splits in two once you
 * know who is looking: "waiting" means *you* still have to say yes, or *they*
 * do. Those are opposite prompts — one is a task for the reader, the other is
 * not — and the PRD's own label, "Waiting on you", is only correct from one
 * side of the pair.
 */
enum class WatchlistSection(val title: String) {
    /** Both are in. Top of the list, because this is what they can act on. */
    Ready("Ready to watch"),

    /** This user has not committed yet — a task, not a passive row. */
    WaitingOnYou("Waiting on you"),

    /** This user is in; the partner has not answered. */
    WaitingOnThem("Waiting on them"),

    Watched("Watched"),
}

data class WatchlistRow(
    val item: WatchlistItem,
    val film: Film?,
    val section: WatchlistSection,
    /** Both partners' Taste Dial scores, once watched. Empty until then. */
    val scores: Map<String, Double> = emptyMap(),
) {
    val id: String get() = item.id

    /** "Added by you" / "Added by your partner" / "From a match". */
    fun attribution(session: Session): String = when {
        item.source == "match" -> "From a match"
        item.addedBy == session.uid -> "Added by you"
        else -> "Added by your partner"
    }
}

fun sectionOf(item: WatchlistItem, session: Session): WatchlistSection = when {
    item.status == "watched" -> WatchlistSection.Watched
    item.status == "ready" -> WatchlistSection.Ready
    // Read the caller's own flag, exactly as the match screen does — this
    // decides whether the row shows an "I'm in too" button or a quiet wait.
    session.isUserA && !item.commitStatus.userA -> WatchlistSection.WaitingOnYou
    !session.isUserA && !item.commitStatus.userB -> WatchlistSection.WaitingOnYou
    else -> WatchlistSection.WaitingOnThem
}

/**
 * Group and order the list.
 *
 * Watched items sort by shared satisfaction rather than by date (PRD §7.4 item
 * 5) — a list of good evenings is worth more than a reverse-chronological log.
 * Everything else sorts newest first, because an unanswered item is a task and
 * the newest task is the live one.
 */
fun groupWatchlist(
    items: List<WatchlistItem>,
    session: Session,
    films: Map<String, Film>,
    scoresByFilm: Map<String, Map<String, Double>> = emptyMap(),
): Map<WatchlistSection, List<WatchlistRow>> {
    val rows = items.map { item ->
        WatchlistRow(
            item = item,
            film = films[item.filmId],
            section = sectionOf(item, session),
            scores = scoresByFilm[item.filmId].orEmpty(),
        )
    }

    return WatchlistSection.entries.associateWith { section ->
        val inSection = rows.filter { it.section == section }
        if (section == WatchlistSection.Watched) {
            inSection.sortedByDescending { it.item.mutualScore ?: Double.NEGATIVE_INFINITY }
        } else {
            inSection.sortedByDescending { it.item.addedAt?.toDate()?.time ?: 0L }
        }
    }.filterValues { it.isNotEmpty() }
}
