import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { COLLECTIONS, db, pairRef, userRef } from "../lib/firebase";
import { loadUser } from "../lib/pairs";
import { messages } from "../notifications/messages";
import { sendNotification } from "../notifications/send";
import type { PairDoc } from "../types";

/**
 * Join a pair with an invite code.
 *
 * Server-side so the code, its expiry and the "seat still free" check happen
 * atomically — the security rules deliberately close client writes to /pairs.
 *
 * Error cases here map onto the documented ALI-73 states: Wrong Code and an
 * expired/consumed invite.
 */
export const joinPair = onCall<{ inviteCode: string; timezone?: string }>(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in first.");

  const inviteCode = (request.data?.inviteCode ?? "").trim().toUpperCase();
  if (!inviteCode) throw new HttpsError("invalid-argument", "Enter an invite code.");

  const user = await loadUser(uid);
  if (user.pairId) throw new HttpsError("failed-precondition", "You are already paired.");

  const found = await db
    .collection(COLLECTIONS.pairs)
    .where("inviteCode", "==", inviteCode)
    .limit(1)
    .get();

  const pairSnapshot = found.docs[0];
  if (!pairSnapshot) {
    throw new HttpsError("not-found", "That code doesn't match any invite.");
  }

  const pair = pairSnapshot.data() as PairDoc;
  if (pair.userA === uid) {
    throw new HttpsError("failed-precondition", "That's your own invite code.");
  }
  if (pair.userB !== null) {
    throw new HttpsError("failed-precondition", "This invite has already been used.");
  }
  if (pair.inviteCodeExpiresAt.toMillis() <= Date.now()) {
    throw new HttpsError("deadline-exceeded", "This invite code has expired.");
  }

  await db.runTransaction(async (tx) => {
    // Re-read inside the transaction so two people cannot claim the same seat.
    const fresh = await tx.get(pairRef(pairSnapshot.id));
    const freshPair = fresh.data() as PairDoc | undefined;
    if (!freshPair || freshPair.userB !== null) {
      throw new HttpsError("failed-precondition", "This invite has already been used.");
    }

    tx.update(pairRef(pairSnapshot.id), {
      userB: uid,
      status: "both_rating",
      joinedAt: Timestamp.now(),
    });
    tx.update(userRef(uid), {
      pairId: pairSnapshot.id,
      timezone: request.data?.timezone || user.timezone || freshPair.timezone,
      updatedAt: FieldValue.serverTimestamp(),
    });
  });

  // "Partner Joined" — fired here rather than from an onPairUpdate trigger so
  // it cannot double-fire on unrelated pair writes.
  const copy = messages.partnerJoined(user.name);
  await sendNotification(pair.userA, "partner_joined", {
    title: copy.title,
    body: copy.body,
    pairId: pairSnapshot.id,
  });

  return { pairId: pairSnapshot.id, partnerUid: pair.userA };
});
