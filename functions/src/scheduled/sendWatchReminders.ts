import { Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { db } from "../lib/firebase";
import { loadPair } from "../lib/pairs";
import { messages } from "../notifications/messages";
import { notifyBoth } from "../notifications/send";
import { getFilm } from "../tmdb/cache";
import type { MatchDoc } from "../types";

/** How far ahead of the agreed time the reminder goes out. */
export const REMINDER_LEAD_MINUTES = 15;

/**
 * "Ready to watch Dune? 🍿" — fires 15 minutes before an agreed watch time.
 *
 * Runs every 5 minutes over a collection-group query, so the lead time is
 * accurate to within one tick without a per-match scheduled job.
 */
export const sendWatchReminders = onSchedule(
  { schedule: "every 5 minutes", timeZone: "UTC", secrets: ["TMDB_ACCESS_TOKEN"] },
  async () => {
    const now = Date.now();
    const windowEnd = Timestamp.fromMillis(now + REMINDER_LEAD_MINUTES * 60 * 1000);

    const due = await db
      .collectionGroup("matches")
      .where("reminderSent", "==", false)
      .where("scheduledFor", "<=", windowEnd)
      .limit(200)
      .get();

    if (due.empty) return;

    let sent = 0;
    for (const doc of due.docs) {
      const match = doc.data() as MatchDoc & { scheduledFor?: Timestamp };
      // pairs/{pairId}/matches/{matchId}
      const pairId = doc.ref.parent.parent?.id;
      if (!pairId) continue;

      // Skip anything whose time has already passed by more than the lead —
      // a late reminder for a finished evening is noise, not a nudge.
      const scheduledMs = match.scheduledFor?.toMillis() ?? 0;
      if (scheduledMs < now - REMINDER_LEAD_MINUTES * 60 * 1000) {
        await doc.ref.update({ reminderSent: true });
        continue;
      }

      try {
        const [pair, film] = await Promise.all([loadPair(pairId), getFilm(match.filmId)]);
        const copy = messages.scheduledReminder(film?.title ?? "your film");

        await notifyBoth([pair.userA, pair.userB], "scheduled_reminder", {
          title: copy.title,
          body: copy.body,
          pairId,
          matchId: doc.id,
          filmId: match.filmId,
        });
        await doc.ref.update({ reminderSent: true });
        sent += 1;
      } catch (error) {
        logger.error("Reminder failed", { pairId, matchId: doc.id, error: String(error) });
      }
    }

    logger.info("sendWatchReminders complete", { due: due.size, sent });
  }
);
