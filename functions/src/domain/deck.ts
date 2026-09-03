/**
 * Ordering for the onboarding rating deck.
 *
 * Kept separate from the callable so the part most likely to go wrong — the
 * era spread — can be tested without touching TMDB.
 */

/**
 * Round-robin the era buckets into one deck.
 *
 * A deck that opens with four 2020s films in a row teaches the profile that the
 * user likes the 2020s before they have seen anything else, so buckets are
 * interleaved rather than concatenated. Buckets that run out are skipped; the
 * rest keep going.
 */
export function interleave<T>(buckets: T[][]): T[] {
  const depth = buckets.reduce((max, bucket) => Math.max(max, bucket.length), 0);
  const out: T[] = [];

  for (let i = 0; i < depth; i += 1) {
    for (const bucket of buckets) {
      const item = bucket[i];
      if (item !== undefined) out.push(item);
    }
  }

  return out;
}

/**
 * How many films to draw from each era window for a deck of `size`.
 *
 * Every window gets at least one slot: a window rounded down to zero would
 * silently remove a whole era from the profile.
 */
export function quotasFor(size: number, shares: number[]): number[] {
  return shares.map((share) => Math.max(1, Math.round(size * share)));
}
