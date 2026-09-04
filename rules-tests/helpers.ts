import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import {
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { Timestamp, doc, setDoc } from "firebase/firestore";

const here = dirname(fileURLToPath(import.meta.url));

/** The real rules file — not a copy, so these tests cannot drift from it. */
export const RULES = readFileSync(join(here, "..", "firestore.rules"), "utf8");

export const ALI = "uid_ali";
export const SARA = "uid_sara";
export const STRANGER = "uid_stranger";
export const PAIR = "pair_1";

export async function makeTestEnv(): Promise<RulesTestEnvironment> {
  return initializeTestEnvironment({
    projectId: "moviemate-test",
    firestore: { rules: RULES, host: "127.0.0.1", port: 8080 },
  });
}

const HOUR = 60 * 60 * 1000;

/**
 * Seed a pair with both members, plus one suggested match and one watchlist
 * item, writing with rules bypassed so the fixtures themselves are not the
 * thing under test.
 */
export async function seed(env: RulesTestEnvironment): Promise<void> {
  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();

    await setDoc(doc(db, "users", ALI), {
      uid: ALI,
      name: "Ali",
      email: "ali@example.com",
      emailVerified: true,
      pairId: PAIR,
      onboardingComplete: true,
      ratingCount: 10,
      fcmTokens: [],
      notificationSettings: { dailyMatch: true, partnerActivity: true, reminders: true },
      timezone: "UTC",
    });

    await setDoc(doc(db, "users", SARA), {
      uid: SARA,
      name: "Sara",
      email: "sara@example.com",
      emailVerified: true,
      pairId: PAIR,
      onboardingComplete: true,
      ratingCount: 10,
      fcmTokens: [],
      notificationSettings: { dailyMatch: true, partnerActivity: true, reminders: true },
      timezone: "UTC",
    });

    await setDoc(doc(db, "pairs", PAIR), {
      userA: ALI,
      userB: SARA,
      inviteCode: "MVMT-ABC123",
      inviteCodeExpiresAt: Timestamp.fromMillis(Date.now() + 168 * HOUR),
      status: "active",
      createdAt: Timestamp.now(),
      aBothOnboarded: true,
      streakCount: 3,
      lastMatchGeneratedAt: Timestamp.now(),
      lastWatchAt: null,
      timezone: "UTC",
    });

    await setDoc(doc(db, "pairs", PAIR, "matches", "match_1"), {
      filmId: "438631",
      score: 98,
      reason: "You both love Sci-Fi",
      suggestedAt: Timestamp.now(),
      status: "suggested",
      attemptNumber: 1,
      commitStatus: { userA: false, userB: false },
      bothConfirmedAt: null,
      watchedConfirmedAt: null,
      shortlist: [],
    });

    await setDoc(doc(db, "pairs", PAIR, "watchlist", "item_1"), {
      filmId: "667538",
      addedBy: SARA,
      addedAt: Timestamp.now(),
      source: "manual_search",
      status: "waiting",
      commitStatus: { userA: false, userB: true },
      watchedAt: null,
      mutualScore: null,
    });

    await setDoc(doc(db, "filmCache", "438631"), {
      tmdbId: "438631",
      title: "Dune",
      genres: ["Science Fiction"],
      releaseYear: 2021,
      tmdbRating: 7.8,
      countries: ["US"],
      cachedAt: Timestamp.now(),
      expiresAt: Timestamp.fromMillis(Date.now() + 100 * 24 * HOUR),
    });
  });
}
