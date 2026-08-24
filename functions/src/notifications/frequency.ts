import { Timestamp } from "firebase-admin/firestore";
import type { UserDoc } from "../types";
import { NOTIFICATION_SPECS, type NotificationType } from "./types";

/**
 * Frequency-cap rules from docs/MovieMate-Notification-Architecture.md §7,
 * expressed as pure functions so they can be unit tested without Firestore.
 */

/** A user who opened the app within this window is considered "already here". */
export const ACTIVE_SESSION_WINDOW_MS = 5 * 60 * 1000;

export type SuppressionReason =
  | "no_tokens"
  | "setting_disabled"
  | "active_session"
  | "collapsed"
  | "daily_cap";

export interface CapDecision {
  send: boolean;
  reason?: SuppressionReason;
}

export interface CapContext {
  now: number;
  /** Epoch ms of the last notification of this same type, if any. */
  lastOfTypeAt?: number;
  /** Count of non-essential notifications already delivered today. */
  nonEssentialSentToday: number;
}

function toMillis(value: Timestamp | null | undefined): number | undefined {
  return value ? value.toMillis() : undefined;
}

export function shouldSend(
  user: Pick<UserDoc, "fcmTokens" | "notificationSettings" | "lastActiveAt">,
  type: NotificationType,
  context: CapContext
): CapDecision {
  if (!user.fcmTokens || user.fcmTokens.length === 0) {
    return { send: false, reason: "no_tokens" };
  }

  const spec = NOTIFICATION_SPECS[type];
  if (!user.notificationSettings?.[spec.setting]) {
    return { send: false, reason: "setting_disabled" };
  }

  // Someone who is looking at the app right now does not need to be pinged
  // about it. Applies to every type, essential included.
  const lastActive = toMillis(user.lastActiveAt);
  if (lastActive !== undefined && context.now - lastActive < ACTIVE_SESSION_WINDOW_MS) {
    return { send: false, reason: "active_session" };
  }

  if (
    context.lastOfTypeAt !== undefined &&
    context.now - context.lastOfTypeAt < spec.collapseWindowMinutes * 60 * 1000
  ) {
    return { send: false, reason: "collapsed" };
  }

  if (!spec.essential && context.nonEssentialSentToday >= 1) {
    return { send: false, reason: "daily_cap" };
  }

  return { send: true };
}
