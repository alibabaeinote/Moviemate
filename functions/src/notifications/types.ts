import type { NotificationSettings } from "../types";

/** The seven notification types from docs/MovieMate-Notification-Architecture.md §3. */
export type NotificationType =
  | "daily_match"
  | "partner_joined"
  | "partner_rated"
  | "partner_committed"
  | "both_confirmed"
  | "scheduled_reminder"
  | "watchlist_activity";

/** Where tapping the notification should land (Navigation Compose route key). */
export type DeepLinkTarget = "match" | "watchlist" | "rate" | "reminder" | "onboarding" | "home";

export interface NotificationSpec {
  /** Which of the three user-facing settings toggles gates this type. */
  readonly setting: keyof NotificationSettings;
  readonly target: DeepLinkTarget;
  /**
   * Essential notifications bypass the once-a-day cap. Only the two that carry
   * a genuine, time-bound call to action qualify — everything else is subject
   * to the cap (docs §7).
   */
  readonly essential: boolean;
  /**
   * Collapse window in minutes: a second notification of this type inside the
   * window is dropped rather than delivered.
   */
  readonly collapseWindowMinutes: number;
}

export const NOTIFICATION_SPECS: Record<NotificationType, NotificationSpec> = {
  daily_match: {
    setting: "dailyMatch",
    target: "match",
    essential: true,
    collapseWindowMinutes: 60 * 12,
  },
  partner_joined: {
    setting: "partnerActivity",
    target: "onboarding",
    essential: true,
    collapseWindowMinutes: 60,
  },
  partner_rated: {
    setting: "partnerActivity",
    target: "rate",
    essential: false,
    collapseWindowMinutes: 60,
  },
  partner_committed: {
    setting: "partnerActivity",
    target: "match",
    essential: false,
    collapseWindowMinutes: 10,
  },
  both_confirmed: {
    setting: "reminders",
    target: "reminder",
    essential: true,
    collapseWindowMinutes: 10,
  },
  scheduled_reminder: {
    setting: "reminders",
    target: "match",
    essential: true,
    collapseWindowMinutes: 60,
  },
  watchlist_activity: {
    setting: "partnerActivity",
    target: "watchlist",
    essential: false,
    collapseWindowMinutes: 10,
  },
};
