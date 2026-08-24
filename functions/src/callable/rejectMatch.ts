import { Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { ALGORITHM_CONFIG } from "../config/algorithm";
import { matchesRef } from "../lib/firebase";
import { loadPairAsMember } from "../lib/pairs";
import type { MatchDoc } from "../types";

/**
 * "Not feeling it" — advance to the next candidate on the shortlist.
 *
 * Sequential, never a menu (PRD §7.1): showing several options at once
 * re-creates the disagreement the app exists to remove. Only after all three
 * attempts are rejected does the client show the 3-up fallback screen, and the
 * shortlist stored on the match document is what it renders.
 */
export const rejectMatch = onCall<{ pairId: string; matchId: string }>(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in first.");

  const { pairId, matchId } = request.data ?? {};
  if (!pairId || !matchId) {
    throw new HttpsError("invalid-argument", "pairId and matchId are required.");
  }

  await loadPairAsMember(pairId, uid);

  const ref = matchesRef(pairId).doc(matchId);

  return await ref.firestore.runTransaction(async (tx) => {
    const snapshot = await tx.get(ref);
    if (!snapshot.exists) throw new HttpsError("not-found", "Match not found.");

    const match = snapshot.data() as MatchDoc;
    if (match.status !== "suggested") {
      throw new HttpsError("failed-precondition", "This match is no longer open.");
    }

    const nextAttempt = match.attemptNumber + 1;
    const shortlist = match.shortlist ?? [];

    // Either partner rejecting ends this attempt for both — the decision is
    // shared, so we do not wait for the second person to also say no.
    if (nextAttempt > ALGORITHM_CONFIG.maxAttemptsBeforeFallback || shortlist.length < nextAttempt) {
      tx.update(ref, {
        status: "dismissed",
        dismissedAt: Timestamp.now(),
        fallbackUnlocked: true,
      });
      logger.info("Shortlist exhausted; 3-up fallback unlocked", { pairId, matchId });
      return { exhausted: true, attemptNumber: match.attemptNumber, shortlist };
    }

    const next = shortlist[nextAttempt - 1]!;
    tx.update(ref, {
      filmId: next.filmId,
      score: next.score,
      reason: next.reason,
      attemptNumber: nextAttempt,
      suggestedAt: Timestamp.now(),
      // A new film needs fresh consent from both sides.
      commitStatus: { userA: false, userB: false },
      bothConfirmedAt: null,
    });

    return { exhausted: false, attemptNumber: nextAttempt, filmId: next.filmId };
  });
});
