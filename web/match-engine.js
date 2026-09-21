// Pure recommendation-engine logic, ported from functions/src/domain/
// (tasteProfile.ts, scoring.ts, reason.ts, streak.ts) and functions/src/config/
// algorithm.ts + product.ts so the no-Blaze web client can build today's match
// the same way generateDailyMatch/onMatchUpdate would, without Cloud Functions.
//
// Deliberate deviation from the server version: no country signal. TMDB's
// /discover/movie never returns production_countries (only /movie/{id} does),
// and fetching that per candidate — up to candidatePoolSize of them — is too
// many extra requests for a client build to make eagerly. Its 0.15 weight is
// redistributed onto genre and era below. Everything else mirrors the server
// algorithm exactly, including the config values.

export const ALGORITHM_CONFIG = {
  signalWeights: {
    genre: 0.7,
    era: 0.3,
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
  onboardingRatingTarget: 10,
  // Smaller than the server's 200 — this comes from live TMDB discover calls
  // made from the browser, not a pre-warmed Firestore cache, so every extra
  // candidate is a real extra request. 60 (3 pages) is enough width for a
  // two-person pool.
  candidatePoolSize: 60,
};

const PRODUCT_CONFIG = {
  streakGraceDays: 7,
};

/** Bucket a release year the same way the server does: pre-2000 collapses to one bucket. */
export function decadeKey(releaseYear) {
  if (!Number.isFinite(releaseYear) || releaseYear < 2000) return "pre-2000";
  return `${Math.floor(releaseYear / 10) * 10}s`;
}

function add(into, key, score, weight) {
  const bucket = into[key] ?? { total: 0, weight: 0 };
  bucket.total += score * weight;
  bucket.weight += weight;
  into[key] = bucket;
}

function finalize(acc) {
  const out = {};
  for (const [key, bucket] of Object.entries(acc)) {
    if (bucket.weight > 0) out[key] = bucket.total / bucket.weight;
  }
  return out;
}

/** ratedFilms: [{ filmId, genres, releaseYear, score }] */
export function buildTasteProfile(ratedFilms) {
  const genres = {};
  const eras = {};

  for (const film of ratedFilms) {
    const weight = 1;
    for (const genre of film.genres) add(genres, genre, film.score, weight);
    add(eras, decadeKey(film.releaseYear), film.score, weight);
  }

  return {
    genreAffinity: finalize(genres),
    eraAffinity: finalize(eras),
    sampleSize: ratedFilms.length,
  };
}

function meanAffinity(affinity, keys, neutral) {
  const known = keys.map((key) => affinity[key]).filter((v) => v !== undefined);
  if (known.length === 0) return neutral;
  return known.reduce((sum, v) => sum + v, 0) / known.length;
}

export function predictScore(profile, film, config = ALGORITHM_CONFIG) {
  const { genre, era } = config.signalWeights;
  const neutral = config.neutralAffinity;

  const genreScore = meanAffinity(profile.genreAffinity, film.genres, neutral);
  const eraScore = meanAffinity(profile.eraAffinity, [decadeKey(film.releaseYear)], neutral);

  return genre * genreScore + era * eraScore;
}

function sharedAffinity(a, b, key) {
  const scoreA = a[key];
  const scoreB = b[key];
  if (scoreA === undefined || scoreB === undefined) return undefined;
  return Math.min(scoreA, scoreB);
}

function readableDecade(key) {
  return key === "pre-2000" ? "pre-2000s" : key;
}

export function buildReason(profileA, profileB, film, config = ALGORITHM_CONFIG) {
  const genreHits = film.genres
    .map((genre) => ({ genre, shared: sharedAffinity(profileA.genreAffinity, profileB.genreAffinity, genre) }))
    .filter((hit) => hit.shared !== undefined)
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

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

/** film: { filmId, title, posterPath, genres, releaseYear, tmdbRating } */
export function scoreCandidate(profileA, profileB, film, config = ALGORITHM_CONFIG) {
  const predictedA = predictScore(profileA, film, config);
  const predictedB = predictScore(profileB, film, config);

  const avgScore = (predictedA + predictedB) / 2;
  const divergence = Math.abs(predictedA - predictedB);
  const tasteScore = avgScore - divergence * config.divergencePenalty;

  const qualityBonus = (film.tmdbRating / 10) * 100;
  const finalScore = tasteScore * config.tasteWeight + qualityBonus * config.qualityWeight;

  return {
    film,
    finalScore: clamp(finalScore, 0, 100),
    reason: buildReason(profileA, profileB, film, config),
    // Kept for debugging and for tuning the weights against real data, same
    // as the server's own scoring.ts — and so this can be unit tested the
    // same way scoring.test.ts tests the server version.
    breakdown: { predictedA, predictedB, divergence, tasteScore, qualityBonus },
  };
}

export function rankCandidates(profileA, profileB, candidates, config = ALGORITHM_CONFIG) {
  const ranked = candidates
    .map((film) => scoreCandidate(profileA, profileB, film, config))
    .sort((a, b) => b.finalScore - a.finalScore);

  const top = ranked[0];
  if (!top || top.finalScore < config.noMatchThreshold) {
    return { ranked, noMatches: true };
  }
  return { ranked, noMatches: false };
}

const DAY_MS = 24 * 60 * 60 * 1000;

function localDayKey(date) {
  // The Firestore Timestamps this runs on are moments in time, not
  // wall-clock strings, and the two users can be in different zones anyway —
  // so unlike the server (which keys off the pair's stored timezone), this
  // keys off UTC calendar days. Documented deviation: a watch just before and
  // just after local midnight in a non-UTC zone can land a day off from what
  // the server version would compute.
  return date.toISOString().slice(0, 10);
}

/** state: { count, lastWatchAt: Date|null }. Mirrors domain/streak.ts exactly, minus the timezone parameter. */
export function advanceStreak(state, watchedAt, config = PRODUCT_CONFIG) {
  if (!state.lastWatchAt) {
    return { count: 1, changed: true, reason: "first_watch" };
  }

  const previousDay = localDayKey(state.lastWatchAt);
  const currentDay = localDayKey(watchedAt);

  if (previousDay === currentDay) {
    return { count: state.count, changed: false, reason: "same_day" };
  }

  const gapDays = Math.round(
    (Date.parse(`${currentDay}T00:00:00Z`) - Date.parse(`${previousDay}T00:00:00Z`)) / DAY_MS
  );

  if (gapDays > 0 && gapDays <= config.streakGraceDays) {
    return { count: state.count + 1, changed: true, reason: "continued" };
  }

  return { count: 1, changed: true, reason: "reset" };
}
