import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions";
import { pairRef } from "../lib/firebase";
import { sideOf } from "../lib/pairs";
import type { PairDoc, UserDoc } from "../types";

/**
 * Copy name and avatar onto the pair document whenever either one changes.
 *
 * The security rules let a user read only their own users/{uid} document
 * (`request.auth.uid == userId`) — that is what keeps one partner from reading
 * the other's fcmTokens or timezone. But it also means neither partner has any
 * way to see the other's name or picture, so the one thing the Us screen
 * genuinely needs has to live somewhere both of them can read: the pair
 * document they already share.
 */
export const onUserProfileUpdated = onDocumentUpdated(
  "users/{uid}",
  async (event) => {
    const before = event.data?.before.data() as UserDoc | undefined;
    const after = event.data?.after.data() as UserDoc | undefined;
    if (!before || !after) return;

    if (before.name === after.name && before.avatarUrl === after.avatarUrl) return;
    if (!after.pairId) return;

    const ref = pairRef(after.pairId);
    const snapshot = await ref.get();
    const pair = snapshot.data() as PairDoc | undefined;
    if (!pair) return;

    const { uid } = event.params;
    const side = sideOf(pair, uid);
    if (!side) return;

    const update: Partial<PairDoc> =
      side === "userA"
        ? { userAName: after.name, userAAvatarUrl: after.avatarUrl }
        : { userBName: after.name, userBAvatarUrl: after.avatarUrl };

    await ref.update(update);
    logger.info("Denormalized profile onto pair", { uid, pairId: after.pairId, side });
  }
);
