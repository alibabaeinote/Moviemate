/**
 * Timezone helpers.
 *
 * The daily match is due at 9am *local to each pair* (Backend Schema §3), so the
 * scheduler runs hourly in UTC and asks each pair whether it is 9am for them.
 */

/** Local hour (0-23) in the given IANA zone, falling back to UTC on a bad zone. */
export function localHourIn(timezone: string, at: Date = new Date()): number {
  try {
    const formatted = new Intl.DateTimeFormat("en-US", {
      timeZone: timezone,
      hour: "numeric",
      hour12: false,
    }).format(at);
    const hour = Number.parseInt(formatted, 10);
    return Number.isFinite(hour) ? hour % 24 : at.getUTCHours();
  } catch {
    return at.getUTCHours();
  }
}

/** Local calendar day as "YYYY-MM-DD", used to detect "already ran today". */
export function localDayKey(timezone: string, at: Date = new Date()): string {
  try {
    return new Intl.DateTimeFormat("en-CA", {
      timeZone: timezone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).format(at);
  } catch {
    return at.toISOString().slice(0, 10);
  }
}

/** True when `previous` falls on an earlier local day than `at`. */
export function isNewLocalDay(
  timezone: string,
  previous: Date | null,
  at: Date = new Date()
): boolean {
  if (!previous) return true;
  return localDayKey(timezone, previous) !== localDayKey(timezone, at);
}
