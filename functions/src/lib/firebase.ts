import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";

if (getApps().length === 0) {
  initializeApp();
}

export const db = getFirestore();
export const messaging = getMessaging();

export const COLLECTIONS = {
  users: "users",
  pairs: "pairs",
  filmCache: "filmCache",
  notificationLog: "notificationLog",
} as const;

export const SUBCOLLECTIONS = {
  ratings: "ratings",
  matches: "matches",
  watchlist: "watchlist",
} as const;

export const pairRef = (pairId: string) => db.collection(COLLECTIONS.pairs).doc(pairId);
export const userRef = (userId: string) => db.collection(COLLECTIONS.users).doc(userId);
export const ratingsRef = (pairId: string) => pairRef(pairId).collection(SUBCOLLECTIONS.ratings);
export const matchesRef = (pairId: string) => pairRef(pairId).collection(SUBCOLLECTIONS.matches);
export const watchlistRef = (pairId: string) =>
  pairRef(pairId).collection(SUBCOLLECTIONS.watchlist);
export const filmCacheRef = (filmId: string) =>
  db.collection(COLLECTIONS.filmCache).doc(filmId);

/** Deterministic rating id, so a re-rate updates rather than duplicating. */
export const ratingId = (userId: string, filmId: string) => `${userId}_${filmId}`;
