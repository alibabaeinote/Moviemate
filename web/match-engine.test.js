import { describe, expect, it } from "vitest";
import {
  ALGORITHM_CONFIG,
  advanceStreak,
  buildReason,
  buildTasteProfile,
  decadeKey,
  predictScore,
  rankCandidates,
  scoreCandidate,
} from "./match-engine.js";

/**
 * Ported from functions/test/{tasteProfile,scoring,streak}.test.ts — the
 * server's own tests for the exact logic this file re-implements. Adapted
 * for the two documented deviations: no country signal (so no countries
 * field, no country-weighting assertions), and advanceStreak takes no
 * timezone parameter (keys off UTC calendar days always — see the last
 * test in the advanceStreak block, which pins that deviation explicitly).
 */

const film = (over) => ({
  filmId: over.filmId,
  genres: ["Drama"],
  releaseYear: 2021,
  tmdbRating: 7,
  ...over,
});

const candidate = (over) => ({
  filmId: over.filmId,
  title: over.title || over.filmId,
  posterPath: null,
  genres: ["Sci-Fi"],
  releaseYear: 2021,
  tmdbRating: 7,
  ...over,
});

describe("decadeKey", () => {
  it("buckets modern years by decade", () => {
    expect(decadeKey(2024)).toBe("2020s");
    expect(decadeKey(2020)).toBe("2020s");
    expect(decadeKey(2019)).toBe("2010s");
    expect(decadeKey(2000)).toBe("2000s");
  });

  it("collapses everything before 2000 into one bucket", () => {
    expect(decadeKey(1999)).toBe("pre-2000");
    expect(decadeKey(1954)).toBe("pre-2000");
  });

  it("does not throw on garbage years", () => {
    expect(decadeKey(Number.NaN)).toBe("pre-2000");
  });
});

describe("buildTasteProfile", () => {
  it("averages scores per genre across films", () => {
    const profile = buildTasteProfile([
      film({ filmId: "1", score: 80, genres: ["Sci-Fi"] }),
      film({ filmId: "2", score: 60, genres: ["Sci-Fi"] }),
      film({ filmId: "3", score: 20, genres: ["Comedy"] }),
    ]);

    expect(profile.genreAffinity["Sci-Fi"]).toBe(70);
    expect(profile.genreAffinity["Comedy"]).toBe(20);
    expect(profile.sampleSize).toBe(3);
  });

  it("credits a multi-genre film to every one of its genres", () => {
    const profile = buildTasteProfile([
      film({ filmId: "1", score: 90, genres: ["Sci-Fi", "Drama"] }),
    ]);

    expect(profile.genreAffinity["Sci-Fi"]).toBe(90);
    expect(profile.genreAffinity["Drama"]).toBe(90);
  });

  it("builds era affinity alongside genre", () => {
    const profile = buildTasteProfile([
      film({ filmId: "1", score: 85, releaseYear: 2022 }),
      film({ filmId: "2", score: 45, releaseYear: 1994 }),
    ]);

    expect(profile.eraAffinity["2020s"]).toBe(85);
    expect(profile.eraAffinity["pre-2000"]).toBe(45);
  });

  it("returns an empty profile for a user who has rated nothing", () => {
    const profile = buildTasteProfile([]);
    expect(profile.genreAffinity).toEqual({});
    expect(profile.sampleSize).toBe(0);
  });
});

describe("predictScore", () => {
  const target = candidate({ filmId: "c1" });

  it("applies the configured genre/era signal weights", () => {
    const profile = buildTasteProfile([
      film({ filmId: "1", score: 100, genres: ["Sci-Fi"], releaseYear: 2021 }),
    ]);
    // Both dimensions are 100, so the weighted sum is 100 regardless of split.
    expect(predictScore(profile, target)).toBeCloseTo(100, 6);
  });

  it("weights genre more heavily than era", () => {
    const genreOnly = buildTasteProfile([
      film({ filmId: "1", score: 100, genres: ["Sci-Fi"], releaseYear: 1980 }),
    ]);
    const eraOnly = buildTasteProfile([
      film({ filmId: "1", score: 100, genres: ["Horror"], releaseYear: 2021 }),
    ]);

    expect(predictScore(genreOnly, target)).toBeGreaterThan(predictScore(eraOnly, target));
  });

  it("falls back to the neutral midpoint for unseen dimensions", () => {
    const profile = buildTasteProfile([]);
    expect(predictScore(profile, target)).toBeCloseTo(ALGORITHM_CONFIG.neutralAffinity, 6);
  });

  it("stays inside 0-100 for the extremes of the Taste Dial", () => {
    const hated = buildTasteProfile([
      film({ filmId: "1", score: 0, genres: ["Sci-Fi"], releaseYear: 2021 }),
    ]);
    const loved = buildTasteProfile([
      film({ filmId: "1", score: 100, genres: ["Sci-Fi"], releaseYear: 2021 }),
    ]);

    expect(predictScore(hated, target)).toBeGreaterThanOrEqual(0);
    expect(predictScore(loved, target)).toBeLessThanOrEqual(100);
  });
});

describe("scoreCandidate", () => {
  it("penalises divergence: a shared 70/70 beats a lopsided 95/45", () => {
    const sciFiFan = buildTasteProfile([
      film({ filmId: "a1", score: 95, genres: ["Sci-Fi"] }),
      film({ filmId: "a2", score: 95, genres: ["Sci-Fi"], releaseYear: 2022 }),
    ]);
    const sciFiSkeptic = buildTasteProfile([
      film({ filmId: "b1", score: 45, genres: ["Sci-Fi"] }),
      film({ filmId: "b2", score: 45, genres: ["Sci-Fi"], releaseYear: 2022 }),
    ]);
    const bothLukewarm = buildTasteProfile([
      film({ filmId: "c1", score: 70, genres: ["Sci-Fi"] }),
      film({ filmId: "c2", score: 70, genres: ["Sci-Fi"], releaseYear: 2022 }),
    ]);

    const target = candidate({ filmId: "x" });
    const lopsided = scoreCandidate(sciFiFan, sciFiSkeptic, target);
    const shared = scoreCandidate(bothLukewarm, bothLukewarm, target);

    // Both average to the same predicted score, but only one is a shared taste.
    expect(lopsided.breakdown.predictedA + lopsided.breakdown.predictedB).toBeCloseTo(
      shared.breakdown.predictedA + shared.breakdown.predictedB,
      6
    );
    expect(shared.finalScore).toBeGreaterThan(lopsided.finalScore);
  });

  it("applies the divergence penalty at the configured rate", () => {
    const a = buildTasteProfile([film({ filmId: "1", score: 100, genres: ["Sci-Fi"] })]);
    const b = buildTasteProfile([film({ filmId: "1", score: 0, genres: ["Sci-Fi"] })]);
    const result = scoreCandidate(a, b, candidate({ filmId: "x" }));

    const { predictedA, predictedB, divergence, tasteScore } = result.breakdown;
    expect(divergence).toBeCloseTo(Math.abs(predictedA - predictedB), 6);
    expect(tasteScore).toBeCloseTo(
      (predictedA + predictedB) / 2 - divergence * ALGORITHM_CONFIG.divergencePenalty,
      6
    );
  });

  it("scales the TMDB quality bonus onto the same 0-100 axis as taste", () => {
    const profile = buildTasteProfile([film({ filmId: "1", score: 70, genres: ["Sci-Fi"] })]);
    const perfect = scoreCandidate(profile, profile, candidate({ filmId: "c1", tmdbRating: 10 }));
    const awful = scoreCandidate(profile, profile, candidate({ filmId: "c2", tmdbRating: 0 }));

    expect(perfect.breakdown.qualityBonus).toBe(100);
    expect(awful.breakdown.qualityBonus).toBe(0);
    expect(perfect.finalScore - awful.finalScore).toBeCloseTo(100 * ALGORITHM_CONFIG.qualityWeight, 6);
  });

  it("lets shared taste outrank general acclaim, as the low quality weight intends", () => {
    const loversOfHorror = buildTasteProfile([
      film({ filmId: "1", score: 95, genres: ["Horror"] }),
      film({ filmId: "2", score: 95, genres: ["Horror"], releaseYear: 2022 }),
    ]);
    const belovedButWrongGenre = candidate({ filmId: "acclaimed", genres: ["Musical"], tmdbRating: 9.5 });
    const ourKindOfFilm = candidate({ filmId: "ours", genres: ["Horror"], tmdbRating: 6.0 });

    const acclaimed = scoreCandidate(loversOfHorror, loversOfHorror, belovedButWrongGenre);
    const ours = scoreCandidate(loversOfHorror, loversOfHorror, ourKindOfFilm);

    expect(ours.finalScore).toBeGreaterThan(acclaimed.finalScore);
  });

  it("never returns a score outside 0-100", () => {
    const hater = buildTasteProfile([film({ filmId: "1", score: 0, genres: ["Sci-Fi"] })]);
    const lover = buildTasteProfile([film({ filmId: "1", score: 100, genres: ["Sci-Fi"] })]);

    for (const [a, b] of [
      [hater, hater],
      [lover, lover],
      [hater, lover],
    ]) {
      for (const tmdbRating of [0, 5, 10]) {
        const { finalScore } = scoreCandidate(a, b, candidate({ filmId: "x", tmdbRating }));
        expect(finalScore).toBeGreaterThanOrEqual(0);
        expect(finalScore).toBeLessThanOrEqual(100);
      }
    }
  });
});

describe("buildReason", () => {
  it("names the shared genre when affinity clears the strong threshold", () => {
    const fans = buildTasteProfile([
      film({ filmId: "1", score: 90, genres: ["Comedy"] }),
      film({ filmId: "2", score: 90, genres: ["Comedy"], releaseYear: 2022 }),
    ]);
    const reason = buildReason(fans, fans, candidate({ filmId: "x", genres: ["Comedy"] }));
    expect(reason).toBe("You both love Comedy");
  });

  it("falls back to era when no genre clears the threshold", () => {
    const fans = buildTasteProfile([
      film({ filmId: "1", score: 90, genres: ["Drama"], releaseYear: 1985 }),
      film({ filmId: "2", score: 90, genres: ["Drama"], releaseYear: 1988 }),
    ]);
    const reason = buildReason(
      fans,
      fans,
      candidate({ filmId: "x", genres: ["Western"], releaseYear: 1987 })
    );
    expect(reason).toBe("You both gravitate toward pre-2000s films");
  });

  it("gives a generic reason when nothing clears either threshold", () => {
    const neutral = buildTasteProfile([]);
    const reason = buildReason(neutral, neutral, candidate({ filmId: "x" }));
    expect(reason).toBe("A pick that fits both your tastes");
  });
});

describe("rankCandidates", () => {
  const fans = buildTasteProfile([
    film({ filmId: "1", score: 90, genres: ["Sci-Fi"] }),
    film({ filmId: "2", score: 88, genres: ["Sci-Fi"], releaseYear: 2022 }),
  ]);

  it("sorts by finalScore descending", () => {
    const result = rankCandidates(fans, fans, [
      candidate({ filmId: "weak", genres: ["Musical"], tmdbRating: 4 }),
      candidate({ filmId: "strong", genres: ["Sci-Fi"], tmdbRating: 8 }),
      candidate({ filmId: "middling", genres: ["Sci-Fi"], tmdbRating: 5 }),
    ]);

    expect(result.ranked.map((c) => c.film.filmId)).toEqual(["strong", "middling", "weak"]);
    expect(result.noMatches).toBe(false);
  });

  it("reports noMatches instead of suggesting a weak film", () => {
    const mismatched = buildTasteProfile([
      film({ filmId: "1", score: 5, genres: ["Musical"] }),
      film({ filmId: "2", score: 2, genres: ["Musical"], releaseYear: 2022 }),
    ]);
    const result = rankCandidates(mismatched, mismatched, [
      candidate({ filmId: "bad", genres: ["Musical"], tmdbRating: 2 }),
    ]);

    expect(result.noMatches).toBe(true);
    expect(result.ranked[0].finalScore).toBeLessThan(ALGORITHM_CONFIG.noMatchThreshold);
  });

  it("reports noMatches for an empty candidate pool rather than throwing", () => {
    const result = rankCandidates(fans, fans, []);
    expect(result.noMatches).toBe(true);
    expect(result.ranked).toHaveLength(0);
  });
});

describe("advanceStreak", () => {
  const at = (iso) => new Date(iso);

  it("starts at 1 on the pair's first watch", () => {
    const result = advanceStreak({ count: 0, lastWatchAt: null }, at("2026-09-03T20:00:00Z"));
    expect(result).toEqual({ count: 1, changed: true, reason: "first_watch" });
  });

  it("continues on the next day", () => {
    const state = { count: 4, lastWatchAt: at("2026-09-02T20:00:00Z") };
    const result = advanceStreak(state, at("2026-09-03T20:00:00Z"));
    expect(result.count).toBe(5);
    expect(result.reason).toBe("continued");
  });

  it("counts calendar days, not elapsed hours", () => {
    // 23:00 on the 2nd to 20:00 on the 3rd is only 21 hours, but two days.
    const state = { count: 2, lastWatchAt: at("2026-09-02T23:00:00Z") };
    const result = advanceStreak(state, at("2026-09-03T20:00:00Z"));
    expect(result.count).toBe(3);
    expect(result.reason).toBe("continued");
  });

  it("does not count a second film on the same day", () => {
    const state = { count: 3, lastWatchAt: at("2026-09-03T19:00:00Z") };
    const result = advanceStreak(state, at("2026-09-03T22:30:00Z"));
    expect(result).toEqual({ count: 3, changed: false, reason: "same_day" });
  });

  it("survives a gap inside the grace window", () => {
    const state = { count: 6, lastWatchAt: at("2026-09-03T20:00:00Z") };
    const result = advanceStreak(state, at("2026-09-08T20:00:00Z"));
    expect(result.count).toBe(7);
    expect(result.reason).toBe("continued");
  });

  it("resets once the gap exceeds the grace window", () => {
    const state = { count: 12, lastWatchAt: at("2026-09-03T20:00:00Z") };
    const result = advanceStreak(state, at("2026-09-14T20:00:00Z"));
    expect(result).toEqual({ count: 1, changed: true, reason: "reset" });
  });

  it("treats exactly the grace window as still alive", () => {
    const state = { count: 2, lastWatchAt: at("2026-09-03T20:00:00Z") };
    expect(advanceStreak(state, at("2026-09-10T20:00:00Z")).reason).toBe("continued");
    expect(advanceStreak(state, at("2026-09-11T20:00:00Z")).reason).toBe("reset");
  });

  it("resets rather than growing if the clock moves backwards", () => {
    const state = { count: 5, lastWatchAt: at("2026-09-10T20:00:00Z") };
    const result = advanceStreak(state, at("2026-09-03T20:00:00Z"));
    expect(result).toEqual({ count: 1, changed: true, reason: "reset" });
  });

  it("honours a stricter grace window when configured", () => {
    const strict = { streakGraceDays: 1 };
    const state = { count: 3, lastWatchAt: at("2026-09-03T20:00:00Z") };
    expect(advanceStreak(state, at("2026-09-04T20:00:00Z"), strict).reason).toBe("continued");
    expect(advanceStreak(state, at("2026-09-05T20:00:00Z"), strict).reason).toBe("reset");
  });

  it("pins the documented deviation: UTC calendar days, no per-pair timezone", () => {
    // streak.test.ts's server equivalent of this exact instant returns
    // "continued" for a Tokyo-timezoned pair (already the next day there)
    // but "same_day" for UTC. The web version has no timezone parameter, so
    // it always behaves like the UTC case, even for a pair that isn't in UTC.
    const state = { count: 1, lastWatchAt: at("2026-09-03T14:00:00Z") };
    const result = advanceStreak(state, at("2026-09-03T23:30:00Z"));
    expect(result.reason).toBe("same_day");
  });
});
