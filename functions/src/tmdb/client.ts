import { logger } from "firebase-functions";

/**
 * Minimal TMDB v3 client.
 *
 * TMDB is the single source of film metadata (PRD §9). The app never downloads,
 * hosts, or links to the films themselves — metadata display only.
 *
 * Rate limit is ~40-50 req/s per IP, comfortably above MVP scale, but every read
 * still goes through the Firestore cache layer in ./cache.ts first.
 */

const BASE_URL = "https://api.themoviedb.org/3";

export const TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p";

/**
 * Required attribution, shown on the About/Settings screen.
 * https://www.themoviedb.org/about/logos-attribution
 */
export const TMDB_ATTRIBUTION =
  "This product uses the TMDB API but is not endorsed or certified by TMDB.";

export interface TmdbMovie {
  id: number;
  title: string;
  poster_path: string | null;
  overview: string;
  release_date: string;
  vote_average: number;
  vote_count: number;
  genre_ids?: number[];
  genres?: Array<{ id: number; name: string }>;
  runtime?: number;
  production_countries?: Array<{ iso_3166_1: string; name: string }>;
  origin_country?: string[];
}

export interface TmdbPage<T> {
  page: number;
  results: T[];
  total_pages: number;
  total_results: number;
}

export class TmdbError extends Error {
  constructor(
    message: string,
    readonly status: number
  ) {
    super(message);
    this.name = "TmdbError";
  }
}

function authHeaders(): Record<string, string> {
  const token = process.env["TMDB_ACCESS_TOKEN"];
  if (token) {
    return { Authorization: `Bearer ${token}`, accept: "application/json" };
  }
  return { accept: "application/json" };
}

function withApiKey(url: URL): URL {
  if (!process.env["TMDB_ACCESS_TOKEN"]) {
    const apiKey = process.env["TMDB_API_KEY"];
    if (!apiKey) {
      throw new TmdbError("Neither TMDB_ACCESS_TOKEN nor TMDB_API_KEY is configured", 500);
    }
    url.searchParams.set("api_key", apiKey);
  }
  return url;
}

async function request<T>(path: string, params: Record<string, string> = {}): Promise<T> {
  const url = new URL(`${BASE_URL}${path}`);
  for (const [key, value] of Object.entries(params)) {
    url.searchParams.set(key, value);
  }
  withApiKey(url);

  const response = await fetch(url, { headers: authHeaders() });
  if (!response.ok) {
    const body = await response.text().catch(() => "");
    throw new TmdbError(`TMDB ${path} failed: ${response.status} ${body.slice(0, 200)}`, response.status);
  }
  return (await response.json()) as T;
}

/** Full detail for one film, including runtime and production countries. */
export function getMovie(tmdbId: string | number): Promise<TmdbMovie> {
  return request<TmdbMovie>(`/movie/${tmdbId}`);
}

/** Text search — powers the Watchlist manual-add flow (PRD §7.4 item 4). */
export function searchMovies(query: string, page = 1): Promise<TmdbPage<TmdbMovie>> {
  return request<TmdbPage<TmdbMovie>>("/search/movie", {
    query,
    page: String(page),
    include_adult: "false",
    language: "en-US",
  });
}

export interface DiscoverOptions {
  page?: number;
  /** TMDB genre ids to bias the pool toward. */
  withGenres?: number[];
  minVoteCount?: number;
  sortBy?: string;
}

/**
 * Candidate pool for the daily match and the onboarding rating deck.
 *
 * minVoteCount defaults to 200 to keep obscure entries with a single 10/10 vote
 * out of the pool — they would otherwise ride the quality bonus straight to the
 * top of the ranking.
 */
export function discoverMovies(options: DiscoverOptions = {}): Promise<TmdbPage<TmdbMovie>> {
  const params: Record<string, string> = {
    page: String(options.page ?? 1),
    include_adult: "false",
    language: "en-US",
    sort_by: options.sortBy ?? "popularity.desc",
    "vote_count.gte": String(options.minVoteCount ?? 200),
  };
  if (options.withGenres?.length) {
    params["with_genres"] = options.withGenres.join("|");
  }
  return request<TmdbPage<TmdbMovie>>("/discover/movie", params);
}

/** Genre id -> name map, needed to turn discover results into genre strings. */
export async function getGenreMap(): Promise<Map<number, string>> {
  const data = await request<{ genres: Array<{ id: number; name: string }> }>(
    "/genre/movie/list",
    { language: "en-US" }
  );
  return new Map(data.genres.map((g) => [g.id, g.name]));
}

/** Best-effort call that logs and resolves to null instead of failing a batch. */
export async function tryGetMovie(tmdbId: string | number): Promise<TmdbMovie | null> {
  try {
    return await getMovie(tmdbId);
  } catch (error) {
    logger.warn("TMDB detail fetch failed", { tmdbId, error: String(error) });
    return null;
  }
}
