import { Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { COLLECTIONS, db } from "../lib/firebase";

/**
 * Mark invite codes expired 7 days after issue (ALI-73).
 *
 * joinPair already rejects an expired code on read; this run exists so the
 * inviter's own screen can show an accurate "expired — regenerate" state
 * without every client re-deriving it.
 */
export const expireInviteCodes = onSchedule(
  { schedule: "every day 04:00", timeZone: "UTC" },
  async () => {
    const stale = await db
      .collection(COLLECTIONS.pairs)
      .where("userB", "==", null)
      .where("inviteCodeExpiresAt", "<=", Timestamp.now())
      .limit(500)
      .get();

    if (stale.empty) {
      logger.info("expireInviteCodes: nothing to expire");
      return;
    }

    const writer = db.bulkWriter();
    for (const doc of stale.docs) {
      writer.update(doc.ref, { inviteCodeExpired: true });
    }
    await writer.close();

    logger.info("expireInviteCodes complete", { expired: stale.size });
  }
);
