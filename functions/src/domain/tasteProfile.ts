import { ALGORITHM_CONFIG, type AlgorithmConfig } from "../config/algorithm";
import type { RatedFilm, ScorableFilm } from "../types";

/**
 * A user's taste, derived from the films they rated on the Taste Dial.
 *
 * This is a weighted average per dimension, deliberately NOT a learned model:
 * the MVP has to be able to explain to a couple *why* a film was suggested
 * (docs/MovieMate-Recommendation-Algorithm.md §2).
 */
export interface TasteProfile {
  genreAffinity: Record<string, number>;
  eraAffinity: Record<string, number>;
  countryAffinity: Record<string, number>;
  /** How many rated films fed this profile — used to flag cold-start noise. */
  sampleSize: number;
}

/**
 * Bucket a release year into the era key used by eraAffinity.
 * Everything before 2000 collapses into one bucket, matching the design doc's
 * example profile ("2020s", "2010s", "pre-2000").
 */
export function decadeKey(releaseYear: number): string {
  if (!Number.isFinite(releaseYear) || releaseYear < 2000) return "pre-2000";
  return `${Math.floor(releaseYear / 10) * 10}s`;
}

interface Accumulator {
  total: number;
  weight: number;
}

function add(into: Record<string, Accumulator>, key: string, score: number, weight: number): void {
  const bucket = into[key] ?? { total: 0, weight: 0 };
  bucket.total += score * weight;
  bucket.weight += weight;
  into[key] = bucket;
}

function finalize(acc: Record<string, Accumulator>): Record<string, number> {
  const out: Record<string, number> = {};
  for (const [key, bucket] of Object.entries(acc)) {
    if (bucket.weight > 0) out[key] = bucket.total / bucket.weight;
  }
  return out;
}

/**
 * Build a taste profile from a user's ratings.
 *
 * Every rating currently carries weight 1. Recency weighting is a deliberate
 * v2 idea (docs §2) and would slot in here as the `weight` argument.
 */
export function buildTasteProfile(ratedFilms: RatedFilm[]): TasteProfile {
  const genres: Record<string, Accumulator> = {};
  const eras: Record<string, Accumulator> = {};
  const countries: Record<string, Accumulator> = {};

  for (const film of ratedFilms) {
    const weight = 1;
    for (const genre of film.genres) add(genres, genre, film.score, weight);
    add(eras, decadeKey(film.releaseYear), film.score, weight);
    for (const country of film.countries) add(countries, country, film.score, weight);
  }

  return {
    genreAffinity: finalize(genres),
    eraAffinity: finalize(eras),
    countryAffinity: finalize(countries),
    sampleSize: ratedFilms.length,
  };
}

/**
 * Mean affinity across a film's keys, falling back to the neutral midpoint for
 * dimensions this user has never rated. A neutral 50 neither rewards nor
 * punishes a candidate for a signal we have no evidence about.
 */
function meanAffinity(
  affinity: Record<string, number>,
  keys: string[],
  neutral: number
): number {
  const known = keys.map((key) => affinity[key]).filter((v): v is number => v !== undefined);
  if (known.length === 0) return neutral;
  return known.reduce((sum, v) => sum + v, 0) / known.length;
}

/**
 * Predict how much one user would enjoy a candidate film, on the same 0-100
 * scale as the Taste Dial (docs §3).
 */
export function predictScore(
  profile: TasteProfile,
  film: ScorableFilm,
  config: AlgorithmConfig = ALGORITHM_CONFIG
): number {
  const { genre, era, country } = config.signalWeights;
  const neutral = config.neutralAffinity;

  const genreScore = meanAffinity(profile.genreAffinity, film.genres, neutral);
  const eraScore = meanAffinity(profile.eraAffinity, [decadeKey(film.releaseYear)], neutral);
  const countryScore = meanAffinity(profile.countryAffinity, film.countries, neutral);

  return genre * genreScore + era * eraScore + country * countryScore;
}
