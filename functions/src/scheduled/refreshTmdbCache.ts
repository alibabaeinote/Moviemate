import { logger } from "firebase-functions";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { purgeExpired } from "../tmdb/cache";

/**
 * Enforce the TMDB 6-month cache limit.
 *
 * This is a Terms of Use obligation, not an optimisation — do not disable it
 * or lengthen the TTL (PRD §9).
 */
export const refreshTmdbCache = onSchedule(
  { schedule: "every day 03:00", timeZone: "UTC", secrets: ["TMDB_ACCESS_TOKEN"] },
  async () => {
    let total = 0;
    // Purge in batches until a run comes back short, so a large backlog still
    // clears rather than trickling out one batch per day.
    for (let pass = 0; pass < 20; pass += 1) {
      const purged = await purgeExpired();
      total += purged;
      if (purged < 500) break;
    }
    logger.info("refreshTmdbCache complete", { purged: total });
  }
);
