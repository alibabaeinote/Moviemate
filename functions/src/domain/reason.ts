import { ALGORITHM_CONFIG, type AlgorithmConfig } from "../config/algorithm";
import type { ScorableFilm } from "../types";
import { decadeKey, type TasteProfile } from "./tasteProfile";

/**
 * The one-line "why this film" shown under the match card.
 *
 * Kept template-based and traceable to the actual affinity numbers — if we
 * cannot point at a shared signal, we say so plainly rather than inventing a
 * flattering reason (docs/MovieMate-Recommendation-Algorithm.md §7).
 */

/** Lowest affinity of the two users for a key — the genuinely *shared* strength. */
function sharedAffinity(
  a: Record<string, number>,
  b: Record<string, number>,
  key: string
): number | undefined {
  const scoreA = a[key];
  const scoreB = b[key];
  if (scoreA === undefined || scoreB === undefined) return undefined;
  return Math.min(scoreA, scoreB);
}

function readableDecade(key: string): string {
  return key === "pre-2000" ? "pre-2000s" : key;
}

export function buildReason(
  profileA: TasteProfile,
  profileB: TasteProfile,
  film: ScorableFilm,
  config: AlgorithmConfig = ALGORITHM_CONFIG
): string {
  const genreHits = film.genres
    .map((genre) => ({
      genre,
      shared: sharedAffinity(profileA.genreAffinity, profileB.genreAffinity, genre),
    }))
    .filter((hit): hit is { genre: string; shared: number } => hit.shared !== undefined)
    .sort((x, y) => y.shared - x.shared);

  const best = genreHits[0];
  if (best && best.shared > config.reason.strongGenreThreshold) {
    const secondary = genreHits[1];
    return secondary
      ? `You both love ${best.genre} with a ${secondary.genre.toLowerCase()} streak`
      : `You both love ${best.genre}`;
  }

  const era = decadeKey(film.releaseYear);
  const sharedEra = sharedAffinity(profileA.eraAffinity, profileB.eraAffinity, era);
  if (sharedEra !== undefined && sharedEra > config.reason.strongEraThreshold) {
    return `You both gravitate toward ${readableDecade(era)} films`;
  }

  return "A pick that fits both your tastes";
}
