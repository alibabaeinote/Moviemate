/**
 * Mutual score: the pair's combined verdict on a film they watched.
 *
 * Defined as the mean of the two Taste Dial scores (PRD §7.4 item 5). It is
 * what the Watched section of the Watchlist sorts by, so it only exists once
 * BOTH people have rated — a single score is one person's opinion, not a
 * shared one.
 */

export interface PartnerScores {
  /** Post-watch Taste Dial score for userA, or undefined if not yet rated. */
  a?: number;
  b?: number;
}

/** Null until both have rated. Rounded to one decimal to keep sorting stable. */
export function computeMutualScore(scores: PartnerScores): number | null {
  if (scores.a === undefined || scores.b === undefined) return null;
  return Math.round(((scores.a + scores.b) / 2) * 10) / 10;
}

/** How far apart the two of them landed — surfaced on the Watched row. */
export function scoreDivergence(scores: PartnerScores): number | null {
  if (scores.a === undefined || scores.b === undefined) return null;
  return Math.abs(scores.a - scores.b);
}
