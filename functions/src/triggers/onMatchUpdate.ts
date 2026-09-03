import { Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { pairRef, userRef } from "../lib/firebase";
import { loadPair } from "../lib/pairs";
import { advanceStreak } from "../domain/streak";
import {
  markWatchlistItemWatched,
  promoteMatchToWatchlist,
} from "../domain/watchlistService";
import { messages } from "../notifications/messages";
import { notifyBoth, sendNotification } from "../notifications/send";
import { getFilm } from "../tmdb/cache";
import type { MatchDoc, PairDoc, UserDoc } from "../types";

/**
 * The whole server-side match lifecycle, in one trigger.
 *
 * Both transitions it handles write back to the same match document, so each
 * one re-fires this function. Every branch below is therefore guarded on a
 * before→after edge rather than on the after state alone; without that, the
 * "watched" write would trigger another "watched" write forever.
 *
 * Replaces the separately-named onCommitUpdate from the schema doc — one
 * invocation per write instead of two functions racing on the same document.
 */
export const onMatchUpdate = onDocumentUpdated(
  { document: "pairs/{pairId}/matches/{matchId}", secrets: ["TMDB_ACCESS_TOKEN"] },
  async (event) => {
    const before = event.data?.before.data() as MatchDoc | undefined;
    const after = event.data?.after.data() as MatchDoc | undefined;
    if (!before || !after) return;

    const { pairId, matchId } = event.params;

    // 1. Manual "We watched it" (PRD §10 — always manual, never inferred).
    if (!before.watchedConfirmedAt && after.watchedConfirmedAt) {
      await handleWatched(pairId, matchId, after, event.data!.after.ref);
      return;
    }

    // 2. Mutual commitment reached.
    const wasBoth = before.commitStatus.userA && before.commitStatus.userB;
    const isBoth = after.commitStatus.userA && after.commitStatus.userB;

    if (isBoth && !wasBoth) {
      await handleMutualCommit(pairId, matchId, after, event.data!.after.ref);
      return;
    }

    // 3. One side committed and is waiting on the other.
    if (!isBoth) {
      await notifyOneSidedCommit(pairId, matchId, before, after);
    }
  }
);

async function handleMutualCommit(
  pairId: string,
  matchId: string,
  match: MatchDoc,
  ref: FirebaseFirestore.DocumentReference
): Promise<void> {
  const pair = await loadPair(pairId);
  const title = await filmTitle(match.filmId);

  // bothConfirmedAt is what the Us screen counts real matches by (PRD §9), so
  // it is stamped here and never by a client.
  if (!match.bothConfirmedAt) {
    await ref.update({ bothConfirmedAt: Timestamp.now() });
  }

  // A film both of them agreed to belongs on the shared list, not only on
  // today's match card.
  await promoteMatchToWatchlist(pairId, match.filmId, pair.userA, match.commitStatus);

  logger.info("Mutual commitment reached", { pairId, matchId, filmId: match.filmId });

  const copy = messages.bothConfirmed(title);
  await notifyBoth([pair.userA, pair.userB], "both_confirmed", {
    title: copy.title,
    body: copy.body,
    pairId,
    matchId,
    filmId: match.filmId,
  });
}

/**
 * Close the daily loop: mark the match watched, mirror that onto the watchlist,
 * and advance the pair's streak.
 *
 * Either partner may confirm — the rules allow both, and requiring two taps to
 * record something that already happened would be friction for its own sake.
 */
async function handleWatched(
  pairId: string,
  matchId: string,
  match: MatchDoc,
  ref: FirebaseFirestore.DocumentReference
): Promise<void> {
  const watchedAt = match.watchedConfirmedAt ?? Timestamp.now();

  if (match.status !== "watched") {
    await ref.update({ status: "watched" });
  }

  await markWatchlistItemWatched(pairId, match.filmId, watchedAt);
  await updateStreak(pairId, watchedAt);

  logger.info("Watch cycle closed", { pairId, matchId, filmId: match.filmId });

  // NOTE: there is no notification here. The person who tapped "We watched it"
  // goes straight to the Taste Dial in-app, but their partner currently gets no
  // prompt to rate. Adding an eighth notification type spends from the daily
  // frequency budget, so it needs product sign-off first — tracked in
  // docs/MovieMate-Dev-Checklist.md.
}

/** Advance the streak in a transaction — two matches could close at once. */
async function updateStreak(pairId: string, watchedAt: Timestamp): Promise<void> {
  const ref = pairRef(pairId);

  await ref.firestore.runTransaction(async (tx) => {
    const snapshot = await tx.get(ref);
    const pair = snapshot.data() as PairDoc | undefined;
    if (!pair) return;

    const result = advanceStreak(
      {
        count: pair.streakCount ?? 0,
        lastWatchAt: pair.lastWatchAt?.toDate() ?? null,
      },
      watchedAt.toDate(),
      pair.timezone || "UTC"
    );

    if (!result.changed) return;

    tx.update(ref, { streakCount: result.count, lastWatchAt: watchedAt });
    logger.info("Streak advanced", { pairId, count: result.count, reason: result.reason });
  });
}

/** "Ali wants to watch Dune with you!" */
async function notifyOneSidedCommit(
  pairId: string,
  matchId: string,
  before: MatchDoc,
  after: MatchDoc
): Promise<void> {
  const pair = await loadPair(pairId);

  const actor =
    !before.commitStatus.userA && after.commitStatus.userA
      ? { uid: pair.userA, recipient: pair.userB }
      : !before.commitStatus.userB && after.commitStatus.userB
        ? { uid: pair.userB, recipient: pair.userA }
        : null;

  if (!actor?.uid || !actor.recipient) return;

  const [name, title] = await Promise.all([nameOf(actor.uid), filmTitle(after.filmId)]);
  const copy = messages.partnerCommitted(name, title);

  await sendNotification(actor.recipient, "partner_committed", {
    title: copy.title,
    body: copy.body,
    pairId,
    matchId,
    filmId: after.filmId,
  });
}

async function filmTitle(filmId: string): Promise<string> {
  const film = await getFilm(filmId);
  return film?.title ?? "this film";
}

async function nameOf(uid: string): Promise<string> {
  const snapshot = await userRef(uid).get();
  return (snapshot.data() as UserDoc | undefined)?.name ?? "Your partner";
}
