import { ALGORITHM_CONFIG, type AlgorithmConfig } from "../config/algorithm";
import type { ScorableFilm } from "../types";
import { predictScore, type TasteProfile } from "./tasteProfile";
import { buildReason } from "./reason";

export interface ScoredCandidate {
  film: ScorableFilm;
  /** 0-100. Stored in matches.score and shown in the UI as "N% shared taste". */
  finalScore: number;
  reason: string;
  /** Kept for debugging and for tuning the weights against real data. */
  breakdown: {
    predictedA: number;
    predictedB: number;
    divergence: number;
    tasteScore: number;
    qualityBonus: number;
  };
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

/**
 * Combine two individual predictions into a joint match score (docs §4-§6).
 *
 * The divergence penalty is the heart of this: a film predicted 95 for one
 * person and 40 for the other averages to a respectable 67.5, but it is not a
 * *shared* pick — it is one person's film. Penalising the gap corrects that.
 */
export function scoreCandidate(
  profileA: TasteProfile,
  profileB: TasteProfile,
  film: ScorableFilm,
  config: AlgorithmConfig = ALGORITHM_CONFIG
): ScoredCandidate {
  const predictedA = predictScore(profileA, film, config);
  const predictedB = predictScore(profileB, film, config);

  const avgScore = (predictedA + predictedB) / 2;
  const divergence = Math.abs(predictedA - predictedB);
  const tasteScore = avgScore - divergence * config.divergencePenalty;

  // TMDB's 0-10 vote average, rescaled to the same 0-100 axis. Its weight is
  // deliberately small: general acclaim must not outrank these two people's
  // taste (docs §5).
  //
  // NOTE — the source doc contradicts itself here. §5 writes
  //   qualityBonus = (tmdbRating / 10) * 10        // = tmdbRating, i.e. 0-10
  // while its own inline comment says "rescale to 0-100", and the §6 summary
  // formula writes (tmdbRating * 10), i.e. 0-100. Taking the §5 arithmetic
  // literally would mix a 0-10 term into a 0-100 blend and silently strip ~90%
  // of the intended quality weight. We follow the stated intent and §6.
  // Flagged for product sign-off — see README §"Open questions".
  const qualityBonus = (film.tmdbRating / 10) * 100;

  const finalScore = tasteScore * config.tasteWeight + qualityBonus * config.qualityWeight;

  return {
    film,
    finalScore: clamp(finalScore, 0, 100),
    reason: buildReason(profileA, profileB, film, config),
    breakdown: { predictedA, predictedB, divergence, tasteScore, qualityBonus },
  };
}

export interface RankingResult {
  /** All candidates, best first. */
  ranked: ScoredCandidate[];
  /** The top `maxAttemptsBeforeFallback` candidates that cleared the threshold. */
  shortlist: ScoredCandidate[];
  /** True when nothing cleared noMatchThreshold — triggers the ALI-73 screen. */
  noMatches: boolean;
}

/**
 * Rank a candidate pool and decide whether there is anything worth suggesting.
 *
 * Returning `noMatches` rather than the least-bad film is intentional: showing a
 * weak pick costs more trust than showing none (docs §8).
 */
export function rankCandidates(
  profileA: TasteProfile,
  profileB: TasteProfile,
  candidates: ScorableFilm[],
  config: AlgorithmConfig = ALGORITHM_CONFIG
): RankingResult {
  const ranked = candidates
    .map((film) => scoreCandidate(profileA, profileB, film, config))
    .sort((a, b) => b.finalScore - a.finalScore);

  const top = ranked[0];
  if (!top || top.finalScore < config.noMatchThreshold) {
    return { ranked, shortlist: [], noMatches: true };
  }

  return {
    ranked,
    shortlist: ranked.slice(0, config.maxAttemptsBeforeFallback),
    noMatches: false,
  };
}
