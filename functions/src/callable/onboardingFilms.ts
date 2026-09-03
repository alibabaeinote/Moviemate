import { HttpsError, onCall } from "firebase-functions/v2/https";
import { logger } from "firebase-functions";
import { PRODUCT_CONFIG } from "../config/product";
import { cacheDiscoverResults } from "../tmdb/cache";
import { discoverMovies, getGenreMap, type TmdbMovie } from "../tmdb/client";
import { interleave, quotasFor } from "../domain/deck";
import type { FilmCacheDoc } from "../types";

/**
 * The onboarding rating deck.
 *
 * Onboarding is two-stage (Recommendation Algorithm §2): pick genres, then rate
 * a deck of films. The deck has to SPAN eras and countries, not just return the
 * most popular titles — a profile built from fifteen recent US blockbusters has
 * no era or country signal at all, and those are 40% of the scoring weight.
 */

/** Stage 1: the genre list the user picks from. */
export const listGenres = onCall(
  { secrets: ["TMDB_ACCESS_TOKEN"] },
  async (request) => {
    if (!request.auth?.uid) throw new HttpsError("unauthenticated", "Sign in first.");

    const genreMap = await getGenreMap();
    return {
      genres: [...genreMap.entries()].map(([id, name]) => ({ id, name })),
    };
  }
);

interface DeckFilm {
  filmId: string;
  title: string;
  posterPath: string | null;
  genres: string[];
  releaseYear: number;
  overview: string;
}

function toDeckFilm(doc: FilmCacheDoc): DeckFilm {
  return {
    filmId: doc.tmdbId,
    title: doc.title,
    posterPath: doc.posterPath,
    genres: doc.genres,
    releaseYear: doc.releaseYear,
    overview: doc.overview,
  };
}

/**
 * Era buckets the deck is spread across, so eraAffinity has something to learn
 * from. Weighted toward recent films because that is what people have seen, but
 * never entirely recent.
 */
const ERA_WINDOWS: Array<{ gte: string; lte: string; share: number }> = [
  { gte: "2020-01-01", lte: "2100-01-01", share: 0.4 },
  { gte: "2010-01-01", lte: "2019-12-31", share: 0.3 },
  { gte: "2000-01-01", lte: "2009-12-31", share: 0.15 },
  { gte: "1950-01-01", lte: "1999-12-31", share: 0.15 },
];

/**
 * Stage 2: a deck of films to rate, spanning the chosen genres and the era
 * windows above.
 */
export const getOnboardingFilms = onCall<{ genreIds?: number[]; size?: number }>(
  { secrets: ["TMDB_ACCESS_TOKEN"] },
  async (request) => {
    if (!request.auth?.uid) throw new HttpsError("unauthenticated", "Sign in first.");

    const size = Math.min(
      Math.max(request.data?.size ?? PRODUCT_CONFIG.onboardingDeckSize, 5),
      30
    );
    const genreIds = request.data?.genreIds ?? [];

    // Pull each era window separately so one bucket cannot crowd out the rest.
    const collected = new Map<string, TmdbMovie>();
    const perWindow: string[][] = [];
    const quotas = quotasFor(size, ERA_WINDOWS.map((w) => w.share));

    for (const [index, window] of ERA_WINDOWS.entries()) {
      const quota = quotas[index]!;
      const response = await discoverMovies({
        withGenres: genreIds,
        minVoteCount: PRODUCT_CONFIG.minVoteCount,
        sortBy: "popularity.desc",
        releasedAfter: window.gte,
        releasedBefore: window.lte,
      });

      const taken: string[] = [];
      for (const movie of response.results) {
        if (taken.length >= quota) break;
        const id = String(movie.id);
        if (collected.has(id)) continue;
        collected.set(id, movie);
        taken.push(id);
      }
      perWindow.push(taken);
    }

    const docs = await cacheDiscoverResults([...collected.values()]);
    const byId = new Map(docs.map((doc) => [doc.tmdbId, doc]));

    // Interleave the windows so the deck does not open with one era in a block.
    const usable = perWindow.map((ids) =>
      ids
        .map((id) => byId.get(id))
        .filter(
          (doc): doc is FilmCacheDoc =>
            doc !== undefined && doc.genres.length > 0 && doc.releaseYear > 0
        )
    );
    const deck = interleave(usable).slice(0, size).map(toDeckFilm);

    logger.info("Onboarding deck built", {
      uid: request.auth.uid,
      requested: size,
      returned: deck.length,
      genreIds,
    });

    if (deck.length === 0) {
      throw new HttpsError("unavailable", "Could not load films right now.");
    }

    return { films: deck };
  }
);
