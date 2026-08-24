/**
 * Tunable constants for the recommendation engine.
 *
 * Every number here is an untested design assumption from
 * docs/MovieMate-Recommendation-Algorithm.md §10 — NOT a validated value.
 * They live in one file precisely so they can be tuned against real user data
 * (measured against the "We're in" rate) without touching the scoring code.
 *
 * Do not inline any of these values elsewhere in the codebase.
 */
export interface AlgorithmConfig {
  /** Weights for predictedScore(user, film). Must sum to 1. */
  readonly signalWeights: {
    readonly genre: number;
    readonly era: number;
    readonly country: number;
  };

  /**
   * How hard to punish a film the two users are predicted to disagree about.
   * tasteScore = avgScore - (divergence * divergencePenalty)
   */
  readonly divergencePenalty: number;

  /** Split between shared taste and TMDB's general quality signal. Must sum to 1. */
  readonly tasteWeight: number;
  readonly qualityWeight: number;

  /**
   * Below this finalScore we show the "No matches" scenario (ALI-73) instead of
   * suggesting a weak film.
   */
  readonly noMatchThreshold: number;

  /**
   * Fallback used when a user has rated nothing in a given dimension. 50 is the
   * neutral midpoint of the 0-100 Taste Dial, so an unknown signal neither helps
   * nor hurts a candidate.
   */
  readonly neutralAffinity: number;

  /** Reason-text generation (docs §7). */
  readonly reason: {
    /** Minimum shared genre affinity before we name the genre explicitly. */
    readonly strongGenreThreshold: number;
    /** Minimum shared era affinity before we fall back to naming the decade. */
    readonly strongEraThreshold: number;
  };

  /** Sequential suggestion logic (PRD §7.1). */
  readonly maxAttemptsBeforeFallback: number;

  /** How many candidates to score per run, after cheap pre-filtering. */
  readonly candidatePoolSize: number;

  /** Films rated during onboarding before a pair is considered ready. */
  readonly onboardingRatingTarget: number;
}

export const ALGORITHM_CONFIG: AlgorithmConfig = {
  signalWeights: {
    genre: 0.6,
    era: 0.25,
    country: 0.15,
  },
  divergencePenalty: 0.4,
  tasteWeight: 0.85,
  qualityWeight: 0.15,
  noMatchThreshold: 40,
  neutralAffinity: 50,
  reason: {
    strongGenreThreshold: 70,
    strongEraThreshold: 70,
  },
  maxAttemptsBeforeFallback: 3,
  candidatePoolSize: 200,
  onboardingRatingTarget: 10,
};
