import { logger } from "firebase-functions";
import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { loadPair } from "../lib/pairs";
import { messages } from "../notifications/messages";
import { notifyBoth } from "../notifications/send";
import { getFilm } from "../tmdb/cache";
import type { WatchlistDoc } from "../types";

/**
 * The same mutual-commitment rule for items added straight to the Watchlist.
 *
 * Drives the three-way split the Watchlist screen renders — Ready once both are
 * in, Waiting on you while one still is not (PRD §7.4).
 */
export const onWatchlistUpdate = onDocumentUpdated(
  { document: "pairs/{pairId}/watchlist/{itemId}", secrets: ["TMDB_ACCESS_TOKEN"] },
  async (event) => {
    const before = event.data?.before.data() as WatchlistDoc | undefined;
    const after = event.data?.after.data() as WatchlistDoc | undefined;
    if (!before || !after) return;

    const wasBoth = before.commitStatus.userA && before.commitStatus.userB;
    const isBoth = after.commitStatus.userA && after.commitStatus.userB;

    // Guard on the edge: this handler writes status back to the same document.
    if (wasBoth || !isBoth) return;
    // A film they already watched does not go back to "ready".
    if (after.status === "watched") return;

    const { pairId, itemId } = event.params;
    await event.data!.after.ref.update({ status: "ready" });

    const pair = await loadPair(pairId);
    const film = await getFilm(after.filmId);
    const copy = messages.bothConfirmed(film?.title ?? "this film");

    logger.info("Watchlist item ready", { pairId, itemId, filmId: after.filmId });

    await notifyBoth([pair.userA, pair.userB], "both_confirmed", {
      title: copy.title,
      body: copy.body,
      pairId,
      matchId: itemId,
      filmId: after.filmId,
    });
  }
);
