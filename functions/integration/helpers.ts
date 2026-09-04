import { Timestamp, getFirestore } from "firebase-admin/firestore";
import functionsTest from "firebase-functions-test";

/**
 * Shared fixtures for the integration suite.
 *
 * These tests run the real trigger and callable handlers against a real
 * Firestore, so what they prove is the multi-document behaviour that unit tests
 * on pure functions cannot: does confirming a watch actually update the match,
 * the watchlist item AND the pair's streak, and does it do so without
 * re-triggering itself.
 */

export const fft = functionsTest({ projectId: "moviemate-test" });

export const ALI = "uid_ali";
export const SARA = "uid_sara";
export const PAIR = "pair_1";
export const FILM = "438631";
export const FILM_B = "667538";

export const db = () => getFirestore();

export const matchPath = (matchId: string) => `pairs/${PAIR}/matches/${matchId}`;
export const watchlistPath = (itemId: string) => `pairs/${PAIR}/watchlist/${itemId}`;

const DAY = 24 * 60 * 60 * 1000;

export interface SeedOptions {
  /** Users start with no FCM tokens so notifications short-circuit before FCM. */
  fcmTokens?: string[];
  aBothOnboarded?: boolean;
  onboardingComplete?: { ali: boolean; sara: boolean };
  streakCount?: number;
  lastWatchAt?: Timestamp | null;
  timezone?: string;
}

export async function seed(options: SeedOptions = {}): Promise<void> {
  const {
    fcmTokens = [],
    aBothOnboarded = true,
    onboardingComplete = { ali: true, sara: true },
    streakCount = 0,
    lastWatchAt = null,
    timezone = "UTC",
  } = options;

  const store = db();

  const user = (uid: string, name: string, done: boolean) => ({
    uid,
    name,
    email: `${name.toLowerCase()}@example.com`,
    emailVerified: true,
    createdAt: Timestamp.now(),
    pairId: PAIR,
    onboardingComplete: done,
    ratingCount: done ? 10 : 0,
    fcmTokens,
    fcmTokenUpdatedAt: null,
    notificationSettings: { dailyMatch: true, partnerActivity: true, reminders: true },
    timezone,
    lastActiveAt: null,
  });

  await store.doc(`users/${ALI}`).set(user(ALI, "Ali", onboardingComplete.ali));
  await store.doc(`users/${SARA}`).set(user(SARA, "Sara", onboardingComplete.sara));

  await store.doc(`pairs/${PAIR}`).set({
    userA: ALI,
    userB: SARA,
    inviteCode: "MVMT-ABC123",
    inviteCodeExpiresAt: Timestamp.fromMillis(Date.now() + 7 * DAY),
    status: "active",
    createdAt: Timestamp.now(),
    aBothOnboarded,
    streakCount,
    lastMatchGeneratedAt: null,
    lastWatchAt,
    timezone,
  });

  // Seeded so getFilm() never has to reach TMDB.
  for (const [id, title] of [[FILM, "Dune"], [FILM_B, "Poor Things"]] as const) {
    await store.doc(`filmCache/${id}`).set({
      tmdbId: id,
      title,
      posterPath: null,
      genres: ["Science Fiction"],
      releaseYear: 2021,
      runtime: 155,
      overview: "",
      tmdbRating: 7.8,
      countries: ["US"],
      cachedAt: Timestamp.now(),
      expiresAt: Timestamp.fromMillis(Date.now() + 180 * DAY),
    });
  }
}

export interface MatchSeed {
  filmId?: string;
  status?: string;
  attemptNumber?: number;
  commitStatus?: { userA: boolean; userB: boolean };
  bothConfirmedAt?: Timestamp | null;
  watchedConfirmedAt?: Timestamp | null;
  shortlist?: Array<{ filmId: string; score: number; reason: string }>;
}

export function matchDoc(over: MatchSeed = {}) {
  return {
    filmId: FILM,
    score: 98,
    reason: "You both love Sci-Fi",
    suggestedAt: Timestamp.now(),
    status: "suggested",
    attemptNumber: 1,
    commitStatus: { userA: false, userB: false },
    bothConfirmedAt: null,
    watchedConfirmedAt: null,
    shortlist: [],
    ...over,
  };
}

export function watchlistDoc(over: Record<string, unknown> = {}) {
  return {
    filmId: FILM,
    addedBy: SARA,
    addedAt: Timestamp.now(),
    source: "manual_search",
    status: "waiting",
    commitStatus: { userA: false, userB: false },
    watchedAt: null,
    mutualScore: null,
    ...over,
  };
}

/** Wipe every collection these tests touch. */
export async function clearAll(): Promise<void> {
  const store = db();
  for (const path of ["users", "pairs", "filmCache", "notificationLog"]) {
    const snapshot = await store.collection(path).get();
    await Promise.all(snapshot.docs.map((d) => store.recursiveDelete(d.ref)));
  }
}

/**
 * Build a before/after change for a Firestore v2 trigger.
 *
 * The `after` snapshot is built from what is actually stored, so a test cannot
 * accidentally assert against a document shape that never existed.
 */
export function changeFor(
  path: string,
  before: Record<string, unknown> | undefined,
  after: Record<string, unknown> | undefined
) {
  const beforeSnap = before
    ? fft.firestore.makeDocumentSnapshot(before, path)
    : fft.firestore.makeDocumentSnapshot({}, path);
  const afterSnap = after
    ? fft.firestore.makeDocumentSnapshot(after, path)
    : fft.firestore.makeDocumentSnapshot({}, path);
  return fft.makeChange(beforeSnap, afterSnap);
}
