import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { userRef } from "../lib/firebase";
import { loadPair, partnerUidOf } from "../lib/pairs";
import { messages } from "../notifications/messages";
import { sendNotification } from "../notifications/send";
import { getFilm } from "../tmdb/cache";
import type { UserDoc, WatchlistDoc } from "../types";

/**
 * "Sara added Poor Things to your list" — partner activity visibility
 * (PRD §7.4 item 2). Only manual additions notify; items promoted from a daily
 * match already had their own notification.
 */
export const onWatchlistAdd = onDocumentCreated(
  "pairs/{pairId}/watchlist/{itemId}",
  async (event) => {
    const item = event.data?.data() as WatchlistDoc | undefined;
    if (!item || item.source !== "manual_search") return;

    const { pairId, itemId } = event.params;
    const pair = await loadPair(pairId);
    const partnerUid = partnerUidOf(pair, item.addedBy);
    if (!partnerUid) return;

    const [adderSnapshot, film] = await Promise.all([
      userRef(item.addedBy).get(),
      getFilm(item.filmId),
    ]);
    const adderName = (adderSnapshot.data() as UserDoc | undefined)?.name ?? "Your partner";

    const copy = messages.watchlistActivity(adderName, film?.title ?? "a film");
    await sendNotification(partnerUid, "watchlist_activity", {
      title: copy.title,
      body: copy.body,
      pairId,
      matchId: itemId,
      filmId: item.filmId,
    });
  }
);
