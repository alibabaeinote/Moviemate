import { Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { matchesRef } from "../lib/firebase";
import { loadPairAsMember } from "../lib/pairs";
import type { MatchDoc } from "../types";

/**
 * Pick one of the three films on the fallback screen (PRD §7.1).
 *
 * Server-side because the security rules deliberately leave `filmId` closed to
 * clients: which film today's match points at is shared state, and letting
 * either partner rewrite it directly would let one of them change the film out
 * from under the other's commitment.
 *
 * Choosing does not commit — it reopens the match on the chosen film with both
 * commit flags cleared, so the pair still has to agree the way they would on
 * any other suggestion.
 */
export const chooseFallbackFilm = onCall<{
  pairId: string;
  matchId: string;
  filmId: string;
}>(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in first.");

  const { pairId, matchId, filmId } = request.data ?? {};
  if (!pairId || !matchId || !filmId) {
    throw new HttpsError("invalid-argument", "pairId, matchId and filmId are required.");
  }

  await loadPairAsMember(pairId, uid);

  const ref = matchesRef(pairId).doc(matchId);

  return await ref.firestore.runTransaction(async (tx) => {
    const snapshot = await tx.get(ref);
    if (!snapshot.exists) throw new HttpsError("not-found", "Match not found.");

    const match = snapshot.data() as MatchDoc;

    // Only reachable once the one-at-a-time sequence is spent. Without this a
    // client could skip the sequence and jump straight to the menu the PRD
    // exists to avoid showing first.
    if (!match.fallbackUnlocked) {
      throw new HttpsError("failed-precondition", "The fallback options aren't open yet.");
    }

    const chosen = (match.shortlist ?? []).find((entry) => entry.filmId === filmId);
    if (!chosen) {
      throw new HttpsError("invalid-argument", "That film isn't one of today's options.");
    }

    tx.update(ref, {
      filmId: chosen.filmId,
      score: chosen.score,
      reason: chosen.reason,
      status: "suggested",
      suggestedAt: Timestamp.now(),
      fallbackUnlocked: false,
      // A chosen film still needs consent from both sides.
      commitStatus: { userA: false, userB: false },
      bothConfirmedAt: null,
    });

    logger.info("Fallback film chosen", { pairId, matchId, filmId: chosen.filmId, by: uid });
    return { filmId: chosen.filmId };
  });
});
