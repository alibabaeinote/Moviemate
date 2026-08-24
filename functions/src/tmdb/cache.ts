import { Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { db, COLLECTIONS, filmCacheRef } from "../lib/firebase";
import type { FilmCacheDoc, ScorableFilm } from "../types";
import { getGenreMap, tryGetMovie, type TmdbMovie } from "./client";

/**
 * TMDB metadata cache.
 *
 * The 6-month TTL is a TMDB Terms of Use requirement, not a tuning knob
 * (PRD §9). Do not raise it.
 */
export const CACHE_TTL_MS = 6 * 30 * 24 * 60 * 60 * 1000; // ~6 months

function releaseYearOf(movie: TmdbMovie): number {
  const year = Number.parseInt(movie.release_date?.slice(0, 4) ?? "", 10);
  return Number.isFinite(year) ? year : 0;
}

function countriesOf(movie: TmdbMovie): string[] {
  if (movie.production_countries?.length) {
    return movie.production_countries.map((c) => c.iso_3166_1);
  }
  return movie.origin_country ?? [];
}

function genresOf(movie: TmdbMovie, genreMap?: Map<number, string>): string[] {
  if (movie.genres?.length) return movie.genres.map((g) => g.name);
  if (movie.genre_ids?.length && genreMap) {
    return movie.genre_ids
      .map((id) => genreMap.get(id))
      .filter((name): name is string => name !== undefined);
  }
  return [];
}

export function toCacheDoc(movie: TmdbMovie, genreMap?: Map<number, string>): FilmCacheDoc {
  const cachedAt = Timestamp.now();
  return {
    tmdbId: String(movie.id),
    title: movie.title,
    posterPath: movie.poster_path,
    genres: genresOf(movie, genreMap),
    releaseYear: releaseYearOf(movie),
    runtime: movie.runtime ?? 0,
    overview: movie.overview ?? "",
    tmdbRating: movie.vote_average ?? 0,
    countries: countriesOf(movie),
    cachedAt,
    expiresAt: Timestamp.fromMillis(cachedAt.toMillis() + CACHE_TTL_MS),
  };
}

export function isExpired(doc: FilmCacheDoc, now = Timestamp.now()): boolean {
  return doc.expiresAt.toMillis() <= now.toMillis();
}

/** The plain shape the recommendation engine consumes. */
export function toScorableFilm(doc: FilmCacheDoc): ScorableFilm {
  return {
    filmId: doc.tmdbId,
    genres: doc.genres,
    releaseYear: doc.releaseYear,
    countries: doc.countries,
    tmdbRating: doc.tmdbRating,
  };
}

/**
 * Read-through cache: Firestore first, TMDB only on a miss or an expired entry.
 */
export async function getFilm(filmId: string): Promise<FilmCacheDoc | null> {
  const snapshot = await filmCacheRef(filmId).get();
  if (snapshot.exists) {
    const cached = snapshot.data() as FilmCacheDoc;
    if (!isExpired(cached)) return cached;
  }

  const movie = await tryGetMovie(filmId);
  if (!movie) return null;

  const fresh = toCacheDoc(movie);
  await filmCacheRef(filmId).set(fresh);
  return fresh;
}

/** Batched read-through for a set of ids, preserving the caching guarantees. */
export async function getFilms(filmIds: string[]): Promise<Map<string, FilmCacheDoc>> {
  const unique = [...new Set(filmIds)];
  const result = new Map<string, FilmCacheDoc>();
  if (unique.length === 0) return result;

  // getAll is capped in practice; chunk to stay well inside limits.
  const CHUNK = 100;
  const misses: string[] = [];

  for (let i = 0; i < unique.length; i += CHUNK) {
    const chunk = unique.slice(i, i + CHUNK);
    const snapshots = await db.getAll(...chunk.map((id) => filmCacheRef(id)));
    for (const snapshot of snapshots) {
      if (!snapshot.exists) {
        misses.push(snapshot.id);
        continue;
      }
      const cached = snapshot.data() as FilmCacheDoc;
      if (isExpired(cached)) misses.push(snapshot.id);
      else result.set(snapshot.id, cached);
    }
  }

  if (misses.length > 0) {
    const fetched = await Promise.all(misses.map((id) => tryGetMovie(id)));
    const writer = db.bulkWriter();
    for (const movie of fetched) {
      if (!movie) continue;
      const doc = toCacheDoc(movie);
      result.set(doc.tmdbId, doc);
      writer.set(filmCacheRef(doc.tmdbId), doc);
    }
    await writer.close();
  }

  return result;
}

/**
 * Write a batch of discover/search results into the cache.
 *
 * Discover results carry genre_ids rather than genre objects and no runtime, so
 * they are stored as a partial record and filled in by getFilm when a film is
 * actually shown in detail.
 */
export async function cacheDiscoverResults(movies: TmdbMovie[]): Promise<FilmCacheDoc[]> {
  if (movies.length === 0) return [];
  const genreMap = await getGenreMap();
  const docs = movies.map((movie) => toCacheDoc(movie, genreMap));

  const writer = db.bulkWriter();
  for (const doc of docs) {
    writer.set(filmCacheRef(doc.tmdbId), doc, { merge: true });
  }
  await writer.close();

  return docs;
}

/**
 * Delete cache entries past their TTL. Deleting rather than refreshing keeps us
 * compliant by default: an entry nobody asks for again simply disappears, and
 * one that is asked for is re-fetched live by getFilm.
 */
export async function purgeExpired(limit = 500): Promise<number> {
  const now = Timestamp.now();
  const stale = await db
    .collection(COLLECTIONS.filmCache)
    .where("expiresAt", "<=", now)
    .limit(limit)
    .get();

  if (stale.empty) return 0;

  const writer = db.bulkWriter();
  for (const doc of stale.docs) writer.delete(doc.ref);
  await writer.close();

  logger.info("Purged expired filmCache entries", { count: stale.size });
  return stale.size;
}
