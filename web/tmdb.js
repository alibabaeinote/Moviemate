import { TMDB_API_KEY } from "./tmdb-config.js";

const BASE = "https://api.themoviedb.org/3";

// Mirrors functions/src/config/product.ts's PRODUCT_CONFIG.minVoteCount —
// filters out obscure entries whose single 10/10 vote would otherwise ride
// straight to the top of a genre pool.
const MIN_VOTE_COUNT = 200;

// Mirrors functions/src/callable/onboardingFilms.ts's ERA_WINDOWS: a deck
// built only from "most popular right now" has no era signal at all, and era
// is part of the recommendation engine's scoring weight. Interleaved below
// so the deck doesn't open with one era in a block.
const ERA_WINDOWS = [
  { gte: "2020-01-01", lte: "2100-01-01", share: 0.4 },
  { gte: "2010-01-01", lte: "2019-12-31", share: 0.3 },
  { gte: "2000-01-01", lte: "2009-12-31", share: 0.15 },
  { gte: "1950-01-01", lte: "1999-12-31", share: 0.15 },
];

function quotasFor(size, shares) {
  return shares.map((share) => Math.max(1, Math.round(size * share)));
}

/** Round-robins the era buckets so one era can't crowd out the rest of the deck. */
function interleave(buckets) {
  const depth = buckets.reduce((max, bucket) => Math.max(max, bucket.length), 0);
  const out = [];
  for (let i = 0; i < depth; i += 1) {
    for (const bucket of buckets) {
      if (bucket[i] !== undefined) out.push(bucket[i]);
    }
  }
  return out;
}

async function tmdbGet(path, params) {
  const url = new URL(BASE + path);
  url.searchParams.set("api_key", TMDB_API_KEY);
  url.searchParams.set("language", "en-US");
  for (const [key, value] of Object.entries(params || {})) {
    if (value !== undefined && value !== null) url.searchParams.set(key, String(value));
  }
  const res = await fetch(url);
  if (!res.ok) {
    if (res.status === 401) {
      throw new Error("TMDB rejected the API key — check web/tmdb-config.js.");
    }
    const body = await res.json().catch(() => ({}));
    throw new Error(body.status_message || `TMDB request failed (${res.status}).`);
  }
  return res.json();
}

/** Stage 1: the genre list the user picks from. */
export async function fetchGenres() {
  const data = await tmdbGet("/genre/movie/list", {});
  return data.genres; // [{id, name}]
}

function toDeckFilm(movie, genreNamesById) {
  return {
    filmId: String(movie.id),
    title: movie.title,
    posterPath: movie.poster_path,
    genres: (movie.genre_ids || []).map((id) => genreNamesById.get(id)).filter(Boolean),
    releaseYear: movie.release_date ? Number(movie.release_date.slice(0, 4)) : 0,
    tmdbRating: movie.vote_average || 0,
  };
}

/**
 * Stage 2: a deck of films to rate, spanning the chosen genres and the era
 * windows above, excluding anything already in `excludeIds`. Mirrors
 * getOnboardingFilms's shape, minus the filmCache write — there's no Cloud
 * Functions instance to own that cache on this path, so the client just
 * re-queries TMDB, which is fine at this app's scale.
 */
export async function fetchOnboardingFilms(genreIds, size, genreNamesById, excludeIds = new Set()) {
  const quotas = quotasFor(size, ERA_WINDOWS.map((w) => w.share));
  const buckets = [];

  for (let index = 0; index < ERA_WINDOWS.length; index += 1) {
    const window = ERA_WINDOWS[index];
    const data = await tmdbGet("/discover/movie", {
      with_genres: genreIds.join(","),
      "vote_count.gte": MIN_VOTE_COUNT,
      sort_by: "popularity.desc",
      "primary_release_date.gte": window.gte,
      "primary_release_date.lte": window.lte,
    });
    const taken = [];
    for (const movie of data.results || []) {
      if (taken.length >= quotas[index]) break;
      if (excludeIds.has(String(movie.id))) continue;
      if (!movie.genre_ids || movie.genre_ids.length === 0) continue;
      taken.push(movie);
    }
    buckets.push(taken);
  }

  return interleave(buckets)
    .slice(0, size)
    .map((movie) => toDeckFilm(movie, genreNamesById));
}

/**
 * Full detail for a set of films, by id — used both to rebuild a taste
 * profile from someone's rating history and to display a match's title and
 * poster (the match doc itself only stores filmId/score/reason; there's no
 * filmCache write on this path to resolve those from). Unlike a discover
 * result, /movie/{id} returns genres as {id, name} objects directly, so no
 * genre-map lookup is needed here. Returns a Map so callers can look up by
 * filmId in O(1).
 */
export async function fetchFilmsByIds(filmIds) {
  const unique = [...new Set(filmIds)];
  const entries = await Promise.all(
    unique.map(async (id) => {
      try {
        const movie = await tmdbGet(`/movie/${id}`, {});
        return [
          id,
          {
            filmId: id,
            title: movie.title,
            posterPath: movie.poster_path,
            genres: (movie.genres || []).map((g) => g.name),
            releaseYear: movie.release_date ? Number(movie.release_date.slice(0, 4)) : 0,
            tmdbRating: movie.vote_average || 0,
          },
        ];
      } catch {
        // A film that has vanished from TMDB (rare) just drops out of the
        // profile rather than failing the whole build.
        return null;
      }
    })
  );
  return new Map(entries.filter(Boolean));
}

/** Convenience for a single film — used to display a match card's poster/title. */
export async function fetchFilmById(filmId) {
  const films = await fetchFilmsByIds([filmId]);
  return films.get(filmId) || null;
}

/**
 * A broad, popularity-sorted candidate pool for the daily match — unlike the
 * onboarding deck, not biased toward any particular genre, mirroring
 * matchService.ts's buildCandidatePool (the taste-profile scoring is what
 * biases the result, not the pool itself).
 */
export async function fetchMatchCandidates(size, excludeIds = new Set()) {
  const pool = new Map();
  const pages = Math.max(1, Math.ceil(size / 20));

  for (let page = 1; page <= pages; page += 1) {
    const data = await tmdbGet("/discover/movie", {
      sort_by: "popularity.desc",
      "vote_count.gte": MIN_VOTE_COUNT,
      page,
    });
    const genreMap = await genreNamesCache();
    for (const movie of data.results || []) {
      const id = String(movie.id);
      if (excludeIds.has(id) || pool.has(id)) continue;
      if (!movie.genre_ids || movie.genre_ids.length === 0) continue;
      if (!movie.release_date) continue;
      pool.set(id, toDeckFilm(movie, genreMap));
    }
    if (pool.size >= size) break;
    if (page >= (data.total_pages || 1)) break;
  }

  return [...pool.values()];
}

let cachedGenreMap = null;
async function genreNamesCache() {
  if (!cachedGenreMap) {
    const genres = await fetchGenres();
    cachedGenreMap = new Map(genres.map((g) => [g.id, g.name]));
  }
  return cachedGenreMap;
}
