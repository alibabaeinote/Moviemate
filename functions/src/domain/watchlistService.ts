import { Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { watchlistRef } from "../lib/firebase";
import type { CommitStatus, WatchlistDoc, WatchlistSource } from "../types";

/**
 * Watchlist writes that have to stay consistent with the match lifecycle.
 *
 * The Watchlist is the shared record of what this pair is going to watch and
 * what they already have, so a film that reached mutual commitment as a daily
 * match belongs in it just as much as one somebody searched for by hand
 * (PRD §7.4).
 */

/** Find the watchlist entry for a film, if the pair already has one. */
export async function findWatchlistItem(
  pairId: string,
  filmId: string
): Promise<{ id: string; data: WatchlistDoc } | null> {
  const snapshot = await watchlistRef(pairId).where("filmId", "==", filmId).limit(1).get();
  const doc = snapshot.docs[0];
  if (!doc) return null;
  return { id: doc.id, data: doc.data() as WatchlistDoc };
}

/**
 * Put a mutually confirmed match into the Watchlist as "ready".
 *
 * Idempotent: a match document can be written several times (commit, schedule,
 * watched), and each of those re-fires the trigger. If the film is already
 * listed we top up its commit flags instead of adding a duplicate row.
 */
export async function promoteMatchToWatchlist(
  pairId: string,
  filmId: string,
  addedBy: string,
  commitStatus: CommitStatus
): Promise<string> {
  const existing = await findWatchlistItem(pairId, filmId);

  if (existing) {
    // Never demote: an item already marked watched stays watched.
    if (existing.data.status !== "watched") {
      await watchlistRef(pairId).doc(existing.id).update({
        status: "ready",
        commitStatus,
      });
    }
    return existing.id;
  }

  const item: WatchlistDoc = {
    filmId,
    addedBy,
    addedAt: Timestamp.now(),
    source: "match" as WatchlistSource,
    status: "ready",
    commitStatus,
    watchedAt: null,
    mutualScore: null,
  };

  const ref = await watchlistRef(pairId).add(item);
  logger.info("Match promoted to watchlist", { pairId, filmId, itemId: ref.id });
  return ref.id;
}

/** Mark the pair's watchlist entry for a film as watched. */
export async function markWatchlistItemWatched(
  pairId: string,
  filmId: string,
  watchedAt: Timestamp
): Promise<void> {
  const existing = await findWatchlistItem(pairId, filmId);
  if (!existing || existing.data.status === "watched") return;

  await watchlistRef(pairId).doc(existing.id).update({ status: "watched", watchedAt });
}
