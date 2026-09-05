import { HttpsError, onCall } from "firebase-functions/v2/https";
import { logger } from "firebase-functions";
import { cacheDiscoverResults } from "../tmdb/cache";
import { searchMovies } from "../tmdb/client";
import type { FilmCacheDoc } from "../types";

/**
 * Manual film search for the Watchlist (PRD §7.4 item 4).
 *
 * Server-side for two reasons that both matter.
 *
 * A film added to the watchlist has to exist in filmCache, or nothing can
 * resolve it: not the list row, not the recommender's exclusion set, not the
 * "both watched it" rating flow. Clients cannot write filmCache — the rules
 * forbid it, because the 6-month TTL is a TMDB licensing condition rather than
 * an optimisation. So the search that produces an addable film has to be the
 * same call that caches it.
 *
 * And it keeps the TMDB bearer token off the device. A token shipped inside an
 * APK is extractable by anyone who downloads it, and it is our rate limit and
 * our terms that they would be spending.
 */

interface SearchResult {
  filmId: string;
  title: string;
  posterPath: string | null;
  genres: string[];
  releaseYear: number;
  overview: string;
}

function toSearchResult(doc: FilmCacheDoc): SearchResult {
  return {
    filmId: doc.tmdbId,
    title: doc.title,
    posterPath: doc.posterPath,
    genres: doc.genres,
    releaseYear: doc.releaseYear,
    overview: doc.overview,
  };
}

export const searchFilms = onCall<{ query?: string; limit?: number }>(
  { secrets: ["TMDB_ACCESS_TOKEN"] },
  async (request) => {
    if (!request.auth?.uid) throw new HttpsError("unauthenticated", "Sign in first.");

    const query = (request.data?.query ?? "").trim();
    if (query.length < MIN_QUERY_LENGTH) {
      throw new HttpsError("invalid-argument", "Type at least two characters.");
    }

    const limit = Math.min(Math.max(request.data?.limit ?? DEFAULT_LIMIT, 1), MAX_LIMIT);

    const response = await searchMovies(query);

    // No vote-count floor here, unlike the discover pool. That threshold keeps
    // obscure entries with a single 10/10 vote out of films nobody asked for —
    // but this user typed the title, so relevance is not in question and
    // hiding the film they searched for would be the failure.
    const usable = response.results.slice(0, limit);

    const docs = await cacheDiscoverResults(usable);
    const films = docs
      .filter((doc) => doc.genres.length > 0 && doc.releaseYear > 0)
      .map(toSearchResult);

    logger.info("Film search", {
      uid: request.auth.uid,
      returned: films.length,
      matched: response.results.length,
    });

    return { films };
  }
);

const MIN_QUERY_LENGTH = 2;
const DEFAULT_LIMIT = 12;
const MAX_LIMIT = 20;
