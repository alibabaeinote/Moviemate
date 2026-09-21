import {
  collection,
  doc,
  getCountFromServer,
  getDocs,
  query,
  runTransaction,
  serverTimestamp,
  Timestamp,
  updateDoc,
  where,
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js";
import { fetchFilmsByIds, fetchMatchCandidates } from "./tmdb.js";
import { ALGORITHM_CONFIG, advanceStreak, buildTasteProfile, rankCandidates } from "./match-engine.js";

const RATING_TARGET = ALGORITHM_CONFIG.onboardingRatingTarget;

/**
 * How many of a user's onboarding ratings exist — the live-truth replacement
 * for users.onboardingComplete, which only the (Blaze-only) onRatingComplete
 * trigger ever sets. See web/README.md "no-Blaze" section.
 */
export async function onboardingRatingCount(db, pairId, uid) {
  const q = query(
    collection(db, "pairs", pairId, "ratings"),
    where("userId", "==", uid),
    where("isInitialOnboarding", "==", true)
  );
  const snapshot = await getCountFromServer(q);
  return snapshot.data().count;
}

/** Both partners done rating, computed live rather than trusting a stored flag. */
export async function isBothOnboarded(db, pairId, pair) {
  if (!pair?.userA || !pair?.userB) return false;
  const [a, b] = await Promise.all([
    onboardingRatingCount(db, pairId, pair.userA),
    onboardingRatingCount(db, pairId, pair.userB),
  ]);
  return a >= RATING_TARGET && b >= RATING_TARGET;
}

/** Rebuild one user's taste profile from their full rating history. */
async function buildProfileFor(db, pairId, uid) {
  const ratingsSnap = await getDocs(
    query(collection(db, "pairs", pairId, "ratings"), where("userId", "==", uid))
  );
  const scoreByFilm = new Map();
  ratingsSnap.forEach((d) => scoreByFilm.set(d.data().filmId, d.data().score));
  if (scoreByFilm.size === 0) return buildTasteProfile([]);

  const films = await fetchFilmsByIds([...scoreByFilm.keys()]);
  const rated = [];
  for (const [filmId, score] of scoreByFilm) {
    const film = films.get(filmId);
    if (film) rated.push({ ...film, score });
  }
  return buildTasteProfile(rated);
}

/** Films neither user should be offered again: already rated, listed or matched. */
async function excludedFilmIds(db, pairId) {
  const [ratings, watchlist, matches] = await Promise.all([
    getDocs(collection(db, "pairs", pairId, "ratings")),
    getDocs(collection(db, "pairs", pairId, "watchlist")),
    getDocs(collection(db, "pairs", pairId, "matches")),
  ]);
  const excluded = new Set();
  ratings.forEach((d) => excluded.add(d.data().filmId));
  watchlist.forEach((d) => excluded.add(d.data().filmId));
  matches.forEach((d) => {
    const filmId = d.data().filmId;
    if (filmId) excluded.add(filmId);
  });
  return excluded;
}

const GENERATION_GATE_MS = 20 * 60 * 60 * 1000; // mirrors createsTodaysMatch()'s duration.value(20, 'h')

/**
 * The no-Blaze fallback for generateDailyMatch: builds both taste profiles,
 * pulls a candidate pool from TMDB, ranks it, and writes today's match doc
 * plus the pair's lastMatchGeneratedAt — the same two documents
 * generateMatchForPair's Admin-SDK write and the scheduled function's own
 * update touch, just as client writes instead of one Admin transaction.
 *
 * The profile-building and TMDB fetch happen once, outside the transaction —
 * a transaction body should only do Firestore reads/writes, not slow
 * external I/O, since Firestore retries the whole body on a conflicting
 * write. The transaction itself only re-checks the 20h gate against the
 * *current* lastMatchGeneratedAt and writes both documents atomically: if
 * two clients call this within the same window, Firestore's optimistic
 * concurrency retries the loser's transaction against the winner's
 * already-committed state, and the gate check below then throws a clean,
 * friendly error instead of a duplicate match ever being written — closing
 * the race a plain batch write left open.
 */
export async function generateTodaysMatch(db, pairId, pair) {
  const [profileA, profileB, excluded] = await Promise.all([
    buildProfileFor(db, pairId, pair.userA),
    buildProfileFor(db, pairId, pair.userB),
    excludedFilmIds(db, pairId),
  ]);

  const candidates = await fetchMatchCandidates(ALGORITHM_CONFIG.candidatePoolSize, excluded);
  const result = rankCandidates(profileA, profileB, candidates);

  const base = {
    suggestedAt: serverTimestamp(),
    attemptNumber: 1,
    commitStatus: { userA: false, userB: false },
    bothConfirmedAt: null,
    watchedConfirmedAt: null,
    watchedConfirmedBy: null,
    shortlist: [],
  };

  const matchData = result.noMatches
    ? {
        ...base,
        filmId: "",
        score: 0,
        reason: "",
        status: "dismissed",
        noMatchesReason:
          candidates.length === 0
            ? "We ran out of fresh films to suggest."
            : "Nothing scored high enough for both of you today.",
      }
    : {
        ...base,
        filmId: result.ranked[0].film.filmId,
        score: Math.round(result.ranked[0].finalScore),
        reason: result.ranked[0].reason,
        status: "suggested",
      };

  const matchRef = doc(collection(db, "pairs", pairId, "matches"));
  const pairRef = doc(db, "pairs", pairId);

  await runTransaction(db, async (tx) => {
    const pairSnap = await tx.get(pairRef);
    const lastGenerated = pairSnap.data()?.lastMatchGeneratedAt;
    if (lastGenerated) {
      const lastMs = lastGenerated.toMillis ? lastGenerated.toMillis() : new Date(lastGenerated).getTime();
      if (Date.now() - lastMs < GENERATION_GATE_MS) {
        throw new Error("Today's match has already been found.");
      }
    }
    tx.set(matchRef, matchData);
    tx.update(pairRef, { lastMatchGeneratedAt: serverTimestamp() });
  });

  return { matchId: matchRef.id, noMatches: result.noMatches, film: result.ranked[0]?.film };
}

/**
 * The no-Blaze fallback for onMatchUpdate's updateStreak: run the same pure
 * advanceStreak() logic (web/match-engine.js) against the pair's current
 * state and write the result. Called by whichever client just confirmed
 * "we watched it" (confirmsWatchedOnly already allowed that write, unchanged).
 */
export async function advancePairStreak(db, pairId, pair, watchedAt = new Date()) {
  const result = advanceStreak(
    { count: pair.streakCount ?? 0, lastWatchAt: pair.lastWatchAt?.toDate?.() ?? null },
    watchedAt
  );
  if (!result.changed) return result;

  await updateDoc(doc(db, "pairs", pairId), {
    streakCount: result.count,
    lastWatchAt: Timestamp.fromDate(watchedAt),
  });
  return result;
}
