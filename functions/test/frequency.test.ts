import { describe, expect, it } from "vitest";
import { Timestamp } from "firebase-admin/firestore";
import { ACTIVE_SESSION_WINDOW_MS, shouldSend, type CapContext } from "../src/notifications/frequency";

const NOW = 1_800_000_000_000;

const user = (over: Partial<Parameters<typeof shouldSend>[0]> = {}) => ({
  fcmTokens: ["token-1"],
  notificationSettings: { dailyMatch: true, partnerActivity: true, reminders: true },
  lastActiveAt: null,
  ...over,
});

const ctx = (over: Partial<CapContext> = {}): CapContext => ({
  now: NOW,
  nonEssentialSentToday: 0,
  ...over,
});

describe("shouldSend", () => {
  it("sends when nothing is blocking", () => {
    expect(shouldSend(user(), "daily_match", ctx())).toEqual({ send: true });
  });

  it("skips users with no registered devices", () => {
    expect(shouldSend(user({ fcmTokens: [] }), "daily_match", ctx())).toEqual({
      send: false,
      reason: "no_tokens",
    });
  });

  it("respects the user's own settings toggles", () => {
    const optedOut = user({
      notificationSettings: { dailyMatch: false, partnerActivity: true, reminders: true },
    });
    expect(shouldSend(optedOut, "daily_match", ctx()).reason).toBe("setting_disabled");
    // A different type gated by a different toggle still goes through.
    expect(shouldSend(optedOut, "watchlist_activity", ctx()).send).toBe(true);
  });

  it("maps each type to the correct settings toggle", () => {
    const remindersOff = user({
      notificationSettings: { dailyMatch: true, partnerActivity: true, reminders: false },
    });
    expect(shouldSend(remindersOff, "scheduled_reminder", ctx()).reason).toBe("setting_disabled");
    expect(shouldSend(remindersOff, "both_confirmed", ctx()).reason).toBe("setting_disabled");
    expect(shouldSend(remindersOff, "partner_rated", ctx()).send).toBe(true);
  });

  it("does not ping a user who is in the app right now", () => {
    const active = user({
      lastActiveAt: Timestamp.fromMillis(NOW - ACTIVE_SESSION_WINDOW_MS + 1_000),
    });
    expect(shouldSend(active, "daily_match", ctx()).reason).toBe("active_session");
  });

  it("does ping a user whose session has gone cold", () => {
    const idle = user({
      lastActiveAt: Timestamp.fromMillis(NOW - ACTIVE_SESSION_WINDOW_MS - 1_000),
    });
    expect(shouldSend(idle, "daily_match", ctx()).send).toBe(true);
  });

  it("collapses a repeat of the same type inside its window", () => {
    // partner_committed collapses within 10 minutes.
    const justSent = ctx({ lastOfTypeAt: NOW - 5 * 60 * 1000 });
    expect(shouldSend(user(), "partner_committed", justSent).reason).toBe("collapsed");

    const longAgo = ctx({ lastOfTypeAt: NOW - 30 * 60 * 1000 });
    expect(shouldSend(user(), "partner_committed", longAgo).send).toBe(true);
  });

  it("caps non-essential notifications at one per day", () => {
    const capped = ctx({ nonEssentialSentToday: 1 });
    expect(shouldSend(user(), "partner_rated", capped).reason).toBe("daily_cap");
  });

  it("lets essential notifications through the daily cap", () => {
    const capped = ctx({ nonEssentialSentToday: 3 });
    expect(shouldSend(user(), "daily_match", capped).send).toBe(true);
    expect(shouldSend(user(), "scheduled_reminder", capped).send).toBe(true);
  });

  it("checks settings before the caps, so an opt-out is never overridden", () => {
    const optedOut = user({
      notificationSettings: { dailyMatch: false, partnerActivity: false, reminders: false },
    });
    expect(shouldSend(optedOut, "daily_match", ctx()).send).toBe(false);
  });
});
