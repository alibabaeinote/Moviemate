import { HttpsError } from "firebase-functions/v2/https";
import { pairRef, userRef } from "./firebase";
import type { PairDoc, PairSide, UserDoc } from "../types";

/** Which side of the pair a uid sits on, or null if they are not a member. */
export function sideOf(pair: PairDoc, uid: string): PairSide | null {
  if (pair.userA === uid) return "userA";
  if (pair.userB === uid) return "userB";
  return null;
}

export function partnerUidOf(pair: PairDoc, uid: string): string | null {
  if (pair.userA === uid) return pair.userB;
  if (pair.userB === uid) return pair.userA;
  return null;
}

export function bothUids(pair: PairDoc): string[] {
  return [pair.userA, pair.userB].filter((uid): uid is string => Boolean(uid));
}

export async function loadPair(pairId: string): Promise<PairDoc> {
  const snapshot = await pairRef(pairId).get();
  if (!snapshot.exists) {
    throw new HttpsError("not-found", "Pair not found.");
  }
  return snapshot.data() as PairDoc;
}

export async function loadUser(uid: string): Promise<UserDoc> {
  const snapshot = await userRef(uid).get();
  if (!snapshot.exists) {
    throw new HttpsError("not-found", "User profile not found.");
  }
  return snapshot.data() as UserDoc;
}

/** Load a pair and assert the caller belongs to it. */
export async function loadPairAsMember(
  pairId: string,
  uid: string
): Promise<{ pair: PairDoc; side: PairSide }> {
  const pair = await loadPair(pairId);
  const side = sideOf(pair, uid);
  if (!side) {
    throw new HttpsError("permission-denied", "You are not a member of this pair.");
  }
  return { pair, side };
}

const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no I/O/0/1 — read aloud safely
const CODE_LENGTH = 6;

/** Invite code in the documented "MVMT-XXXXXX" shape. */
export function generateInviteCode(): string {
  let code = "";
  for (let i = 0; i < CODE_LENGTH; i += 1) {
    code += CODE_ALPHABET[Math.floor(Math.random() * CODE_ALPHABET.length)];
  }
  return `MVMT-${code}`;
}

/** Invite codes expire after 7 days (ALI-73). */
export const INVITE_CODE_TTL_MS = 7 * 24 * 60 * 60 * 1000;
