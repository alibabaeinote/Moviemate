import { Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { ALGORITHM_CONFIG } from "../config/algorithm";
import { matchesRef, ratingsRef, watchlistRef } from "../lib/firebase";
import { cacheDiscoverResults, getFilms, toScorableFilm } from "../tmdb/cache";
import { discoverMovies } from "../tmdb/client";
import type { MatchDoc, PairDoc, RatedFilm, ScorableFilm, ShortlistEntry } from "../types";
import { rankCandidates, type ScoredCandidate } from "./scoring";
import { buildTasteProfile, type TasteProfile } from "./tasteProfile";

/**
 * Everything the daily match needs, assembled once and reused by both
 * generateDailyMatch and rejectMatch.
 */

/** Rebuild one user's taste profile from their stored ratings. */
export async function buildProfileFor(pairId: string, uid: string): Promise<TasteProfile> {
  const snapshot = await ratingsRef(pairId).where("userId", "==", uid).get();
  if (snapshot.empty) return buildTasteProfile([]);

  const scoreByFilm = new Map<string, number>();
  for (const doc of snapshot.docs) {
    const { filmId, score } = doc.data() as { filmId: string; score: number };
    scoreByFilm.set(filmId, score);
  }

  const films = await getFilms([...scoreByFilm.keys()]);
  const rated: RatedFilm[] = [];
  for (const [filmId, score] of scoreByFilm) {
    const film = films.get(filmId);
    if (film) rated.push({ ...toScorableFilm(film), score });
  }

  return buildTasteProfile(rated);
}

/** Films neither user should be offered again: already rated, listed or watched. */
export async function excludedFilmIds(pairId: string): Promise<Set<string>> {
  const [ratings, watchlist, matches] = await Promise.all([
    ratingsRef(pairId).get(),
    watchlistRef(pairId).get(),
    matchesRef(pairId).get(),
  ]);

  const excluded = new Set<string>();
  for (const doc of ratings.docs) excluded.add((doc.data() as { filmId: string }).filmId);
  for (const doc of watchlist.docs) excluded.add((doc.data() as { filmId: string }).filmId);
  for (const doc of matches.docs) {
    const match = doc.data() as MatchDoc;
    if (match.filmId) excluded.add(match.filmId);
    for (const entry of match.shortlist ?? []) excluded.add(entry.filmId);
  }
  return excluded;
}

/**
 * Assemble a candidate pool from TMDB, biased toward the genres this pair
 * actually likes, and cache everything we pull.
 */
export async function buildCandidatePool(
  pairId: string,
  profileA: TasteProfile,
  profileB: TasteProfile
): Promise<ScorableFilm[]> {
  const excluded = await excludedFilmIds(pairId);
  const pool = new Map<string, ScorableFilm>();

  const pages = Math.max(1, Math.ceil(ALGORITHM_CONFIG.candidatePoolSize / 20));
  for (let page = 1; page <= pages; page += 1) {
    const response = await discoverMovies({ page });
    const docs = await cacheDiscoverResults(response.results);
    for (const doc of docs) {
      if (excluded.has(doc.tmdbId) || pool.has(doc.tmdbId)) continue;
      if (doc.genres.length === 0 || doc.releaseYear === 0) continue;
      pool.set(doc.tmdbId, toScorableFilm(doc));
    }
    if (pool.size >= ALGORITHM_CONFIG.candidatePoolSize) break;
    if (page >= response.total_pages) break;
  }

  logger.info("Candidate pool built", {
    pairId,
    size: pool.size,
    profileASample: profileA.sampleSize,
    profileBSample: profileB.sampleSize,
  });

  return [...pool.values()];
}

export function toShortlistEntries(candidates: ScoredCandidate[]): ShortlistEntry[] {
  return candidates.map((candidate) => ({
    filmId: candidate.film.filmId,
    score: Math.round(candidate.finalScore),
    reason: candidate.reason,
  }));
}

export interface GeneratedMatch {
  matchId: string;
  filmId: string;
  score: number;
  reason: string;
  noMatches: boolean;
}

/**
 * Score the pool and write today's match document.
 *
 * When nothing clears the threshold we still write a document — with
 * `noMatchesReason` set and no film — so the client has an unambiguous state to
 * render (ALI-73) rather than an empty collection it has to guess about.
 */
export async function generateMatchForPair(
  pairId: string,
  pair: PairDoc
): Promise<GeneratedMatch | null> {
  if (!pair.userB) return null;

  const [profileA, profileB] = await Promise.all([
    buildProfileFor(pairId, pair.userA),
    buildProfileFor(pairId, pair.userB),
  ]);

  const candidates = await buildCandidatePool(pairId, profileA, profileB);
  const result = rankCandidates(profileA, profileB, candidates);

  const ref = matchesRef(pairId).doc();
  const now = Timestamp.now();

  if (result.noMatches) {
    const doc: MatchDoc = {
      filmId: "",
      score: 0,
      reason: "",
      suggestedAt: now,
      status: "dismissed",
      attemptNumber: 1,
      commitStatus: { userA: false, userB: false },
      bothConfirmedAt: null,
      watchedConfirmedAt: null,
      shortlist: [],
      noMatchesReason:
        candidates.length === 0
          ? "We ran out of fresh films to suggest."
          : "Nothing scored high enough for both of you today.",
    };
    await ref.set(doc);
    logger.info("No match cleared the threshold", { pairId, poolSize: candidates.length });
    return { matchId: ref.id, filmId: "", score: 0, reason: "", noMatches: true };
  }

  const top = result.shortlist[0]!;
  const doc: MatchDoc = {
    filmId: top.film.filmId,
    score: Math.round(top.finalScore),
    reason: top.reason,
    suggestedAt: now,
    status: "suggested",
    attemptNumber: 1,
    commitStatus: { userA: false, userB: false },
    bothConfirmedAt: null,
    watchedConfirmedAt: null,
    shortlist: toShortlistEntries(result.shortlist),
  };
  await ref.set(doc);

  return {
    matchId: ref.id,
    filmId: doc.filmId,
    score: doc.score,
    reason: doc.reason,
    noMatches: false,
  };
}
