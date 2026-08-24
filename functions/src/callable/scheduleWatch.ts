import { Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { matchesRef } from "../lib/firebase";
import { loadPairAsMember } from "../lib/pairs";
import type { MatchDoc } from "../types";

/**
 * Set the agreed watch time for a mutually confirmed match.
 *
 * Gated on bothConfirmedAt so the scheduling step stays behind the mutual
 * commitment (PRD §7.2) — the reminder screen is not reachable one-sided.
 *
 * NOTE: `scheduledFor` is not in the schema doc; the docs describe a
 * "Suggested time" card and a 15-minute reminder without saying where the time
 * is stored. Added here and flagged in README §"Deviations".
 */
export const scheduleWatch = onCall<{ pairId: string; matchId: string; scheduledForMs: number }>(
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in first.");

    const { pairId, matchId, scheduledForMs } = request.data ?? {};
    if (!pairId || !matchId || !Number.isFinite(scheduledForMs)) {
      throw new HttpsError("invalid-argument", "pairId, matchId and scheduledForMs are required.");
    }
    if (scheduledForMs <= Date.now()) {
      throw new HttpsError("invalid-argument", "Pick a time in the future.");
    }

    await loadPairAsMember(pairId, uid);

    const ref = matchesRef(pairId).doc(matchId);
    const snapshot = await ref.get();
    if (!snapshot.exists) throw new HttpsError("not-found", "Match not found.");

    const match = snapshot.data() as MatchDoc;
    if (!match.bothConfirmedAt) {
      throw new HttpsError("failed-precondition", "You both need to be in first.");
    }

    await ref.update({
      scheduledFor: Timestamp.fromMillis(scheduledForMs),
      reminderSent: false,
    });

    return { scheduledFor: scheduledForMs };
  }
);
