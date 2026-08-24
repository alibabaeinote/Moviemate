import { Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { loadPair } from "../lib/pairs";
import { getFilm } from "../tmdb/cache";
import { messages } from "../notifications/messages";
import { notifyBoth, sendNotification } from "../notifications/send";
import { userRef } from "../lib/firebase";
import type { MatchDoc, UserDoc, WatchlistDoc } from "../types";

async function filmTitle(filmId: string): Promise<string> {
  const film = await getFilm(filmId);
  return film?.title ?? "this film";
}

async function nameOf(uid: string): Promise<string> {
  const snapshot = await userRef(uid).get();
  return (snapshot.data() as UserDoc | undefined)?.name ?? "Your partner";
}

/**
 * Mutual commitment (PRD §7.2).
 *
 * Clients may only flip their own commit flag; `bothConfirmedAt` — the field the
 * "Us" screen counts real matches by (PRD §9) — is stamped here so it can never
 * be set by one person alone.
 */
export const onCommitUpdate = onDocumentUpdated(
  "pairs/{pairId}/matches/{matchId}",
  async (event) => {
    const before = event.data?.before.data() as MatchDoc | undefined;
    const after = event.data?.after.data() as MatchDoc | undefined;
    if (!before || !after) return;

    const { pairId, matchId } = event.params;

    const wasBoth = before.commitStatus.userA && before.commitStatus.userB;
    const isBoth = after.commitStatus.userA && after.commitStatus.userB;

    if (wasBoth === isBoth) {
      // No change in mutual state; check for a fresh one-sided commit instead.
      await notifyOneSidedCommit(pairId, matchId, before, after);
      return;
    }

    if (!isBoth) return;

    const pair = await loadPair(pairId);
    const title = await filmTitle(after.filmId);

    await event.data!.after.ref.update({ bothConfirmedAt: Timestamp.now() });
    logger.info("Mutual commitment reached", { pairId, matchId, filmId: after.filmId });

    const copy = messages.bothConfirmed(title);
    await notifyBoth([pair.userA, pair.userB], "both_confirmed", {
      title: copy.title,
      body: copy.body,
      pairId,
      matchId,
      filmId: after.filmId,
    });
  }
);

/** "Ali wants to watch Dune with you!" — one side committed, the other has not. */
async function notifyOneSidedCommit(
  pairId: string,
  matchId: string,
  before: MatchDoc,
  after: MatchDoc
): Promise<void> {
  const pair = await loadPair(pairId);

  const newlyCommitted =
    !before.commitStatus.userA && after.commitStatus.userA
      ? { actor: pair.userA, recipient: pair.userB }
      : !before.commitStatus.userB && after.commitStatus.userB
        ? { actor: pair.userB, recipient: pair.userA }
        : null;

  if (!newlyCommitted?.actor || !newlyCommitted.recipient) return;

  const [name, title] = await Promise.all([
    nameOf(newlyCommitted.actor),
    filmTitle(after.filmId),
  ]);

  const copy = messages.partnerCommitted(name, title);
  await sendNotification(newlyCommitted.recipient, "partner_committed", {
    title: copy.title,
    body: copy.body,
    pairId,
    matchId,
    filmId: after.filmId,
  });
}

/**
 * The same mutual-commitment rule for items added straight to the Watchlist.
 * Drives the three-way status split the Watchlist screen renders
 * (Ready / Waiting on you / Watched — PRD §7.4).
 */
export const onWatchlistCommitUpdate = onDocumentUpdated(
  "pairs/{pairId}/watchlist/{itemId}",
  async (event) => {
    const before = event.data?.before.data() as WatchlistDoc | undefined;
    const after = event.data?.after.data() as WatchlistDoc | undefined;
    if (!before || !after) return;

    const wasBoth = before.commitStatus.userA && before.commitStatus.userB;
    const isBoth = after.commitStatus.userA && after.commitStatus.userB;
    if (wasBoth || !isBoth) return;

    const { pairId, itemId } = event.params;
    const pair = await loadPair(pairId);
    const title = await filmTitle(after.filmId);

    await event.data!.after.ref.update({ status: "ready" });

    const copy = messages.bothConfirmed(title);
    await notifyBoth([pair.userA, pair.userB], "both_confirmed", {
      title: copy.title,
      body: copy.body,
      pairId,
      matchId: itemId,
      filmId: after.filmId,
    });
  }
);
