import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { COLLECTIONS, db, pairRef } from "../lib/firebase";
import { isNewLocalDay, localHourIn } from "../lib/time";
import { generateMatchForPair } from "../domain/matchService";
import { messages } from "../notifications/messages";
import { notifyBoth } from "../notifications/send";
import type { PairDoc } from "../types";

/** The hour, local to each pair, at which the daily match lands. */
export const DAILY_MATCH_LOCAL_HOUR = 9;

/**
 * Runs hourly in UTC and picks out the pairs for whom it is currently 9am
 * local. A single daily UTC run would deliver the match at the wrong time of
 * day for anyone outside UTC.
 */
export const generateDailyMatch = onSchedule(
  {
    schedule: "every 1 hours",
    timeZone: "UTC",
    secrets: ["TMDB_ACCESS_TOKEN"],
    timeoutSeconds: 540,
    memory: "512MiB",
  },
  async () => {
    const now = new Date();

    const ready = await db
      .collection(COLLECTIONS.pairs)
      .where("aBothOnboarded", "==", true)
      .get();

    let generated = 0;
    let skipped = 0;

    for (const doc of ready.docs) {
      const pair = doc.data() as PairDoc;
      const timezone = pair.timezone || "UTC";

      if (localHourIn(timezone, now) !== DAILY_MATCH_LOCAL_HOUR) {
        skipped += 1;
        continue;
      }

      const lastRun = pair.lastMatchGeneratedAt?.toDate() ?? null;
      if (!isNewLocalDay(timezone, lastRun, now)) {
        skipped += 1;
        continue;
      }

      try {
        const result = await generateMatchForPair(doc.id, pair);
        if (!result) continue;

        await pairRef(doc.id).update({
          lastMatchGeneratedAt: Timestamp.now(),
          updatedAt: FieldValue.serverTimestamp(),
        });
        generated += 1;

        if (!result.noMatches) {
          const copy = messages.dailyMatch();
          await notifyBoth([pair.userA, pair.userB], "daily_match", {
            title: copy.title,
            body: copy.body,
            pairId: doc.id,
            matchId: result.matchId,
            filmId: result.filmId,
          });
        }
      } catch (error) {
        // One pair's failure must not stop the rest of the run.
        logger.error("generateDailyMatch failed for pair", { pairId: doc.id, error: String(error) });
      }
    }

    logger.info("generateDailyMatch run complete", {
      candidates: ready.size,
      generated,
      skipped,
    });
  }
);
