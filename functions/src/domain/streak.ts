import { PRODUCT_CONFIG, type ProductConfig } from "../config/product";
import { localDayKey } from "../lib/time";

/**
 * Streak maths, kept pure so the rule can be argued about and tested without
 * touching Firestore.
 *
 * A streak counts completed watch cycles — films the pair actually watched
 * together and confirmed — not app opens. Counting opens would reward the
 * habit of checking a phone rather than the habit of watching together.
 */

export interface StreakState {
  count: number;
  /** When the streak was last advanced, or null if it never has been. */
  lastWatchAt: Date | null;
}

export interface StreakResult {
  count: number;
  /** False when this watch fell on a day already counted. */
  changed: boolean;
  reason: "first_watch" | "continued" | "reset" | "same_day";
}

const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * Advance (or reset) a streak for a watch completed at `watchedAt`.
 *
 * The comparison is on local calendar days, not elapsed hours: watching at
 * 23:00 on Monday and 20:00 on Tuesday is two consecutive days, even though
 * only 21 hours passed.
 */
export function advanceStreak(
  state: StreakState,
  watchedAt: Date,
  timezone: string,
  config: ProductConfig = PRODUCT_CONFIG
): StreakResult {
  if (!state.lastWatchAt) {
    return { count: 1, changed: true, reason: "first_watch" };
  }

  const previousDay = localDayKey(timezone, state.lastWatchAt);
  const currentDay = localDayKey(timezone, watchedAt);

  if (previousDay === currentDay) {
    // Already counted today — a second film tonight is lovely, but it is not a
    // second day.
    return { count: state.count, changed: false, reason: "same_day" };
  }

  const gapDays = Math.round(
    (Date.parse(`${currentDay}T00:00:00Z`) - Date.parse(`${previousDay}T00:00:00Z`)) / DAY_MS
  );

  if (gapDays > 0 && gapDays <= config.streakGraceDays) {
    return { count: state.count + 1, changed: true, reason: "continued" };
  }

  // Lapsed (or the clock moved backwards) — start again at this watch.
  return { count: 1, changed: true, reason: "reset" };
}
