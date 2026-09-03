import { Timestamp } from "firebase-admin/firestore";

/** Which half of a pair a given uid occupies. */
export type PairSide = "userA" | "userB";

export interface NotificationSettings {
  dailyMatch: boolean;
  partnerActivity: boolean;
  reminders: boolean;
}

/** users/{userId} */
export interface UserDoc {
  uid: string;
  name: string;
  email: string;
  emailVerified: boolean;
  createdAt: Timestamp;
  pairId: string | null;
  onboardingComplete: boolean;
  ratingCount: number;
  fcmTokens: string[];
  fcmTokenUpdatedAt: Timestamp | null;
  notificationSettings: NotificationSettings;
  /** IANA zone, e.g. "Europe/Berlin". Drives the local 9am daily match run. */
  timezone: string;
  /** Used by the frequency cap to skip notifying someone already in the app. */
  lastActiveAt: Timestamp | null;
}

export type PairStatus = "waiting_partner" | "both_rating" | "active";

/** pairs/{pairId} */
export interface PairDoc {
  userA: string;
  userB: string | null;
  inviteCode: string;
  inviteCodeExpiresAt: Timestamp;
  status: PairStatus;
  createdAt: Timestamp;
  aBothOnboarded: boolean;
  streakCount: number;
  lastMatchGeneratedAt: Timestamp | null;
  /** When the streak last advanced. Distinct from lastMatchGeneratedAt. */
  lastWatchAt: Timestamp | null;
  /** Copied from the creator so the scheduler can run at each pair's local 9am. */
  timezone: string;
}

/** pairs/{pairId}/ratings/{ratingId} — id convention: `${userId}_${filmId}` */
export interface RatingDoc {
  userId: string;
  filmId: string;
  /** Taste Dial: continuous 0-100. Never a 1-5 integer. */
  score: number;
  isInitialOnboarding: boolean;
  reactionEmoji: string | null;
  ratedAt: Timestamp;
}

export interface CommitStatus {
  userA: boolean;
  userB: boolean;
}

export type MatchStatus = "suggested" | "watched" | "dismissed";

/** pairs/{pairId}/matches/{matchId} */
export interface MatchDoc {
  filmId: string;
  score: number;
  reason: string;
  suggestedAt: Timestamp;
  status: MatchStatus;
  attemptNumber: number;
  commitStatus: CommitStatus;
  bothConfirmedAt: Timestamp | null;
  watchedConfirmedAt: Timestamp | null;
  /**
   * The ranked shortlist this match came from, so onMatchRejected can advance to
   * the next candidate — and the 3-up fallback screen can show all of them —
   * without re-scoring the whole pool.
   */
  shortlist: ShortlistEntry[];
  /** Set when no candidate cleared noMatchThreshold (ALI-73 "No matches"). */
  noMatchesReason?: string;
}

export interface ShortlistEntry {
  filmId: string;
  score: number;
  reason: string;
}

export type WatchlistStatus = "waiting" | "ready" | "watched";
export type WatchlistSource = "match" | "manual_search";

/** pairs/{pairId}/watchlist/{itemId} */
export interface WatchlistDoc {
  filmId: string;
  addedBy: string;
  addedAt: Timestamp;
  source: WatchlistSource;
  status: WatchlistStatus;
  commitStatus: CommitStatus;
  watchedAt: Timestamp | null;
  /** Mean of both Taste Dial scores; drives the "Watched" sort order. */
  mutualScore: number | null;
}

/** filmCache/{filmId} — TMDB metadata, cached for at most 6 months (TMDB ToU). */
export interface FilmCacheDoc {
  tmdbId: string;
  title: string;
  posterPath: string | null;
  genres: string[];
  releaseYear: number;
  runtime: number;
  overview: string;
  tmdbRating: number;
  /** ISO 3166-1 country codes from TMDB production_countries. */
  countries: string[];
  cachedAt: Timestamp;
  expiresAt: Timestamp;
}

/**
 * Plain (Timestamp-free) film shape the scoring code works with, so the engine
 * stays unit-testable without the Admin SDK.
 */
export interface ScorableFilm {
  filmId: string;
  genres: string[];
  releaseYear: number;
  /** ISO 3166-1 country codes. */
  countries: string[];
  /** TMDB's 0-10 vote average. */
  tmdbRating: number;
}

export interface RatedFilm extends ScorableFilm {
  score: number;
}
