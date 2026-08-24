import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { COLLECTIONS, db, messaging, userRef } from "../lib/firebase";
import type { UserDoc } from "../types";
import { shouldSend, type CapContext } from "./frequency";
import { NOTIFICATION_SPECS, type NotificationType } from "./types";

export interface NotificationPayload {
  title: string;
  body: string;
  pairId: string;
  matchId?: string;
  filmId?: string;
}

const START_OF_DAY_MS = 24 * 60 * 60 * 1000;

async function loadCapContext(userId: string, type: NotificationType): Promise<CapContext> {
  const now = Date.now();
  const dayStart = now - (now % START_OF_DAY_MS);

  const log = db.collection(COLLECTIONS.notificationLog);

  const [lastOfType, todaysNonEssential] = await Promise.all([
    log
      .where("userId", "==", userId)
      .where("type", "==", type)
      .orderBy("sentAt", "desc")
      .limit(1)
      .get(),
    log
      .where("userId", "==", userId)
      .where("essential", "==", false)
      .where("sentAt", ">=", Timestamp.fromMillis(dayStart))
      .count()
      .get(),
  ]);

  const last = lastOfType.docs[0]?.data() as { sentAt: Timestamp } | undefined;

  return {
    now,
    ...(last ? { lastOfTypeAt: last.sentAt.toMillis() } : {}),
    nonEssentialSentToday: todaysNonEssential.data().count,
  };
}

/**
 * Send one notification, honouring the user's settings and the frequency caps.
 *
 * Returns whether it was actually delivered — callers should not assume a send.
 */
export async function sendNotification(
  userId: string,
  type: NotificationType,
  payload: NotificationPayload
): Promise<boolean> {
  const snapshot = await userRef(userId).get();
  if (!snapshot.exists) {
    logger.warn("sendNotification: unknown user", { userId, type });
    return false;
  }

  const user = snapshot.data() as UserDoc;
  const spec = NOTIFICATION_SPECS[type];
  const context = await loadCapContext(userId, type);
  const decision = shouldSend(user, type, context);

  if (!decision.send) {
    logger.info("Notification suppressed", { userId, type, reason: decision.reason });
    return false;
  }

  const response = await messaging.sendEachForMulticast({
    tokens: user.fcmTokens,
    notification: { title: payload.title, body: payload.body },
    data: {
      type,
      deepLinkTarget: spec.target,
      pairId: payload.pairId,
      matchId: payload.matchId ?? "",
      filmId: payload.filmId ?? "",
    },
    android: {
      priority: spec.essential ? "high" : "normal",
      notification: { channelId: spec.essential ? "moviemate_essential" : "moviemate_activity" },
    },
  });

  await pruneDeadTokens(userId, user.fcmTokens, response.responses);

  await db.collection(COLLECTIONS.notificationLog).add({
    userId,
    type,
    essential: spec.essential,
    pairId: payload.pairId,
    sentAt: Timestamp.now(),
    successCount: response.successCount,
  });

  logger.info("Notification sent", {
    userId,
    type,
    success: response.successCount,
    failure: response.failureCount,
  });

  return response.successCount > 0;
}

/**
 * Drop tokens FCM has told us are dead. Without this, fcmTokens grows forever
 * across reinstalls and every send fans out to garbage.
 */
async function pruneDeadTokens(
  userId: string,
  tokens: string[],
  responses: Array<{ success: boolean; error?: { code: string } }>
): Promise<void> {
  const dead = tokens.filter((_, index) => {
    const result = responses[index];
    if (!result || result.success) return false;
    const code = result.error?.code ?? "";
    return (
      code === "messaging/registration-token-not-registered" ||
      code === "messaging/invalid-registration-token" ||
      code === "messaging/invalid-argument"
    );
  });

  if (dead.length === 0) return;

  await userRef(userId).update({ fcmTokens: FieldValue.arrayRemove(...dead) });
  logger.info("Pruned dead FCM tokens", { userId, count: dead.length });
}

/** Send the same notification to both members of a pair. */
export async function notifyBoth(
  userIds: Array<string | null>,
  type: NotificationType,
  payload: NotificationPayload
): Promise<void> {
  await Promise.all(
    userIds
      .filter((id): id is string => Boolean(id))
      .map((id) => sendNotification(id, type, payload))
  );
}
