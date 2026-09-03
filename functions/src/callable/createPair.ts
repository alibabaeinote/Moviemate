import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { COLLECTIONS, db, userRef } from "../lib/firebase";
import { generateInviteCode, INVITE_CODE_TTL_MS, loadUser } from "../lib/pairs";
import type { PairDoc } from "../types";

/**
 * Create a pair and return its invite code.
 *
 * Runs server-side because the code has to be unique and its expiry has to be
 * trustworthy — a client-generated code could collide or never expire.
 */
export const createPair = onCall<{ timezone?: string }>(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in first.");

  const user = await loadUser(uid);
  if (user.pairId) {
    throw new HttpsError("failed-precondition", "You are already paired.");
  }

  const timezone = request.data?.timezone || user.timezone || "UTC";
  const now = Timestamp.now();

  // Retry on the (unlikely) collision rather than handing out a duplicate code.
  for (let attempt = 0; attempt < 5; attempt += 1) {
    const inviteCode = generateInviteCode();
    const existing = await db
      .collection(COLLECTIONS.pairs)
      .where("inviteCode", "==", inviteCode)
      .limit(1)
      .get();
    if (!existing.empty) continue;

    const pairDoc: PairDoc = {
      userA: uid,
      userB: null,
      inviteCode,
      inviteCodeExpiresAt: Timestamp.fromMillis(now.toMillis() + INVITE_CODE_TTL_MS),
      status: "waiting_partner",
      createdAt: now,
      aBothOnboarded: false,
      streakCount: 0,
      lastMatchGeneratedAt: null,
      lastWatchAt: null,
      timezone,
    };

    const ref = db.collection(COLLECTIONS.pairs).doc();
    await db.runTransaction(async (tx) => {
      tx.set(ref, pairDoc);
      tx.update(userRef(uid), { pairId: ref.id, timezone, updatedAt: FieldValue.serverTimestamp() });
    });

    return {
      pairId: ref.id,
      inviteCode,
      inviteCodeExpiresAt: pairDoc.inviteCodeExpiresAt.toMillis(),
    };
  }

  throw new HttpsError("internal", "Could not allocate an invite code. Try again.");
});
