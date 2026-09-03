import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { ALGORITHM_CONFIG } from "../config/algorithm";
import { pairRef, ratingsRef, userRef } from "../lib/firebase";
import { loadPair, partnerUidOf } from "../lib/pairs";
import { messages } from "../notifications/messages";
import { sendNotification } from "../notifications/send";
import { computeMutualScore } from "../domain/mutualScore";
import { findWatchlistItem } from "../domain/watchlistService";
import { watchlistRef } from "../lib/firebase";
import type { PairDoc, RatingDoc, UserDoc } from "../types";

/**
 * Keeps the derived onboarding state in sync whenever a rating is written.
 *
 * This lives server-side on purpose (Backend Schema §2.2): if both users submit
 * their last rating at the same moment, two clients each computing
 * `aBothOnboarded` would race and one of them would lose.
 */
export const onRatingComplete = onDocumentWritten(
  "pairs/{pairId}/ratings/{ratingId}",
  async (event) => {
    const after = event.data?.after;
    if (!after?.exists) return; // deletions are disallowed by the rules

    const { pairId } = event.params;
    const rating = after.data() as RatingDoc;
    const uid = rating.userId;

    // A post-watch rating feeds the pair's shared verdict on a film, not their
    // onboarding progress. The two paths share nothing beyond the counter.
    if (!rating.isInitialOnboarding) {
      await updateMutualScore(pairId, rating.filmId);
    }

    const [totalSnapshot, onboardingSnapshot] = await Promise.all([
      ratingsRef(pairId).where("userId", "==", uid).count().get(),
      ratingsRef(pairId)
        .where("userId", "==", uid)
        .where("isInitialOnboarding", "==", true)
        .count()
        .get(),
    ]);

    const ratingCount = totalSnapshot.data().count;
    const onboardingCount = onboardingSnapshot.data().count;
    const onboardingComplete = onboardingCount >= ALGORITHM_CONFIG.onboardingRatingTarget;

    const userSnapshot = await userRef(uid).get();
    const user = userSnapshot.data() as UserDoc | undefined;
    if (!user) {
      logger.warn("onRatingComplete: rating from unknown user", { pairId, uid });
      return;
    }

    const justFinishedOnboarding = onboardingComplete && !user.onboardingComplete;

    await userRef(uid).update({ ratingCount, onboardingComplete });

    if (!justFinishedOnboarding) return;

    const pair = await loadPair(pairId);
    const partnerUid = partnerUidOf(pair, uid);

    if (!partnerUid) {
      // Partner has not joined yet — nothing to compare against.
      return;
    }

    const partnerSnapshot = await userRef(partnerUid).get();
    const partner = partnerSnapshot.data() as UserDoc | undefined;
    const partnerDone = partner?.onboardingComplete === true;

    if (partnerDone && !pair.aBothOnboarded) {
      // Both sides are ready — this is the flag generateDailyMatch waits on.
      await pairRef(pairId).update({
        aBothOnboarded: true,
        status: "active",
        bothOnboardedAt: Timestamp.now(),
        updatedAt: FieldValue.serverTimestamp(),
      });
      logger.info("Pair is now fully onboarded", { pairId });
      return;
    }

    if (!partnerDone) {
      const copy = messages.partnerRated(user.name);
      await sendNotification(partnerUid, "partner_rated", {
        title: copy.title,
        body: copy.body,
        pairId,
      });
    }
  }
);

/**
 * Recompute a film's mutual score once both partners have rated it.
 *
 * This is what the Watched section of the Watchlist sorts by (PRD §7.4 item 5),
 * so it stays null until the second rating lands — one person's score is an
 * opinion, not a shared verdict.
 */
async function updateMutualScore(pairId: string, filmId: string): Promise<void> {
  const item = await findWatchlistItem(pairId, filmId);
  if (!item) return; // rated something that was never on the list

  const pairSnapshot = await pairRef(pairId).get();
  const pair = pairSnapshot.data() as PairDoc | undefined;
  if (!pair?.userB) return;

  const ratings = await ratingsRef(pairId)
    .where("filmId", "==", filmId)
    .where("isInitialOnboarding", "==", false)
    .get();

  const byUser = new Map<string, number>();
  for (const doc of ratings.docs) {
    const { userId, score } = doc.data() as RatingDoc;
    byUser.set(userId, score);
  }

  const mutualScore = computeMutualScore({
    a: byUser.get(pair.userA),
    b: byUser.get(pair.userB),
  });

  if (mutualScore === null || item.data.mutualScore === mutualScore) return;

  await watchlistRef(pairId).doc(item.id).update({ mutualScore, status: "watched" });
  logger.info("Mutual score recorded", { pairId, filmId, mutualScore });
}
