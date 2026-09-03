import { describe, expect, it } from "vitest";
import { PRODUCT_CONFIG } from "../src/config/product";
import { advanceStreak, type StreakState } from "../src/domain/streak";

const at = (iso: string) => new Date(iso);

describe("advanceStreak", () => {
  it("starts at 1 on the pair's first watch", () => {
    const state: StreakState = { count: 0, lastWatchAt: null };
    const result = advanceStreak(state, at("2026-09-03T20:00:00Z"), "UTC");

    expect(result).toEqual({ count: 1, changed: true, reason: "first_watch" });
  });

  it("continues on the next day", () => {
    const state: StreakState = { count: 4, lastWatchAt: at("2026-09-02T20:00:00Z") };
    const result = advanceStreak(state, at("2026-09-03T20:00:00Z"), "UTC");

    expect(result.count).toBe(5);
    expect(result.reason).toBe("continued");
  });

  it("counts calendar days, not elapsed hours", () => {
    // 23:00 Monday to 20:00 Tuesday is only 21 hours, but it is two days.
    const state: StreakState = { count: 2, lastWatchAt: at("2026-09-02T23:00:00Z") };
    const result = advanceStreak(state, at("2026-09-03T20:00:00Z"), "UTC");

    expect(result.count).toBe(3);
    expect(result.reason).toBe("continued");
  });

  it("does not count a second film on the same evening", () => {
    const state: StreakState = { count: 3, lastWatchAt: at("2026-09-03T19:00:00Z") };
    const result = advanceStreak(state, at("2026-09-03T22:30:00Z"), "UTC");

    expect(result).toEqual({ count: 3, changed: false, reason: "same_day" });
  });

  it("survives a gap inside the grace window", () => {
    // The PRD's target user watches 1-2 times a week; a 5-day gap is normal.
    const state: StreakState = { count: 6, lastWatchAt: at("2026-09-03T20:00:00Z") };
    const result = advanceStreak(state, at("2026-09-08T20:00:00Z"), "UTC");

    expect(result.count).toBe(7);
    expect(result.reason).toBe("continued");
  });

  it("resets once the gap exceeds the grace window", () => {
    const state: StreakState = { count: 12, lastWatchAt: at("2026-09-03T20:00:00Z") };
    const result = advanceStreak(state, at("2026-09-14T20:00:00Z"), "UTC");

    expect(result).toEqual({ count: 1, changed: true, reason: "reset" });
  });

  it("treats exactly the grace window as still alive", () => {
    const state: StreakState = { count: 2, lastWatchAt: at("2026-09-03T20:00:00Z") };
    const boundary = at("2026-09-10T20:00:00Z"); // exactly 7 days later

    expect(advanceStreak(state, boundary, "UTC").reason).toBe("continued");
    expect(advanceStreak(state, at("2026-09-11T20:00:00Z"), "UTC").reason).toBe("reset");
  });

  it("uses the pair's timezone to decide what counts as a new day", () => {
    // 2026-09-03T23:30Z is already the 4th in Tokyo but still the 3rd in UTC.
    const state: StreakState = { count: 1, lastWatchAt: at("2026-09-03T14:00:00Z") };
    const later = at("2026-09-03T23:30:00Z");

    expect(advanceStreak(state, later, "UTC").reason).toBe("same_day");
    expect(advanceStreak(state, later, "Asia/Tokyo").reason).toBe("continued");
  });

  it("resets rather than growing if the clock moves backwards", () => {
    const state: StreakState = { count: 5, lastWatchAt: at("2026-09-10T20:00:00Z") };
    const result = advanceStreak(state, at("2026-09-03T20:00:00Z"), "UTC");

    expect(result).toEqual({ count: 1, changed: true, reason: "reset" });
  });

  it("honours a stricter grace window when configured", () => {
    const strict = { ...PRODUCT_CONFIG, streakGraceDays: 1 };
    const state: StreakState = { count: 3, lastWatchAt: at("2026-09-03T20:00:00Z") };

    expect(advanceStreak(state, at("2026-09-04T20:00:00Z"), "UTC", strict).reason)
      .toBe("continued");
    expect(advanceStreak(state, at("2026-09-05T20:00:00Z"), "UTC", strict).reason)
      .toBe("reset");
  });
});
