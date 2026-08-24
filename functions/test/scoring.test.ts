import { describe, expect, it } from "vitest";
import { ALGORITHM_CONFIG } from "../src/config/algorithm";
import { rankCandidates, scoreCandidate } from "../src/domain/scoring";
import { buildTasteProfile } from "../src/domain/tasteProfile";
import type { RatedFilm, ScorableFilm } from "../src/types";

const rated = (score: number, genres: string[], year = 2021, countries = ["US"]): RatedFilm => ({
  filmId: `r-${score}-${genres.join("-")}-${year}`,
  score,
  genres,
  releaseYear: year,
  countries,
  tmdbRating: 7,
});

const candidate = (over: Partial<ScorableFilm> & { filmId: string }): ScorableFilm => ({
  genres: ["Sci-Fi"],
  releaseYear: 2021,
  countries: ["US"],
  tmdbRating: 7,
  ...over,
});

describe("scoreCandidate", () => {
  it("penalises divergence: a shared 70/70 beats a lopsided 95/45", () => {
    const sciFiFan = buildTasteProfile([rated(95, ["Sci-Fi"]), rated(95, ["Sci-Fi"], 2022)]);
    const sciFiSkeptic = buildTasteProfile([rated(45, ["Sci-Fi"]), rated(45, ["Sci-Fi"], 2022)]);
    const bothLukewarm = buildTasteProfile([rated(70, ["Sci-Fi"]), rated(70, ["Sci-Fi"], 2022)]);

    const film = candidate({ filmId: "c1" });
    const lopsided = scoreCandidate(sciFiFan, sciFiSkeptic, film);
    const shared = scoreCandidate(bothLukewarm, bothLukewarm, film);

    // Both average to 70, but only one of them is a shared taste.
    expect(lopsided.breakdown.predictedA + lopsided.breakdown.predictedB).toBeCloseTo(
      shared.breakdown.predictedA + shared.breakdown.predictedB,
      6
    );
    expect(shared.finalScore).toBeGreaterThan(lopsided.finalScore);
  });

  it("applies the divergence penalty at the configured rate", () => {
    const a = buildTasteProfile([rated(100, ["Sci-Fi"], 2021, ["US"])]);
    const b = buildTasteProfile([rated(0, ["Sci-Fi"], 2021, ["US"])]);
    const result = scoreCandidate(a, b, candidate({ filmId: "c1" }));

    const { predictedA, predictedB, divergence, tasteScore } = result.breakdown;
    expect(divergence).toBeCloseTo(Math.abs(predictedA - predictedB), 6);
    expect(tasteScore).toBeCloseTo(
      (predictedA + predictedB) / 2 - divergence * ALGORITHM_CONFIG.divergencePenalty,
      6
    );
  });

  it("scales the TMDB quality bonus onto the same 0-100 axis as taste", () => {
    const profile = buildTasteProfile([rated(70, ["Sci-Fi"])]);
    const perfect = scoreCandidate(profile, profile, candidate({ filmId: "c1", tmdbRating: 10 }));
    const awful = scoreCandidate(profile, profile, candidate({ filmId: "c2", tmdbRating: 0 }));

    expect(perfect.breakdown.qualityBonus).toBe(100);
    expect(awful.breakdown.qualityBonus).toBe(0);
    // A 10-point spread = the full 0.15 quality weight over 100 points.
    expect(perfect.finalScore - awful.finalScore).toBeCloseTo(
      100 * ALGORITHM_CONFIG.qualityWeight,
      6
    );
  });

  it("lets shared taste outrank general acclaim, as the low quality weight intends", () => {
    const loversOfHorror = buildTasteProfile([rated(95, ["Horror"]), rated(95, ["Horror"], 2022)]);
    const belovedButWrongGenre = candidate({
      filmId: "acclaimed",
      genres: ["Musical"],
      tmdbRating: 9.5,
    });
    const ourKindOfFilm = candidate({ filmId: "ours", genres: ["Horror"], tmdbRating: 6.0 });

    const acclaimed = scoreCandidate(loversOfHorror, loversOfHorror, belovedButWrongGenre);
    const ours = scoreCandidate(loversOfHorror, loversOfHorror, ourKindOfFilm);

    expect(ours.finalScore).toBeGreaterThan(acclaimed.finalScore);
  });

  it("never returns a score outside 0-100", () => {
    const hater = buildTasteProfile([rated(0, ["Sci-Fi"], 2021, ["US"])]);
    const lover = buildTasteProfile([rated(100, ["Sci-Fi"], 2021, ["US"])]);

    for (const [a, b] of [
      [hater, hater],
      [lover, lover],
      [hater, lover],
    ] as const) {
      for (const tmdbRating of [0, 5, 10]) {
        const { finalScore } = scoreCandidate(a, b, candidate({ filmId: "x", tmdbRating }));
        expect(finalScore).toBeGreaterThanOrEqual(0);
        expect(finalScore).toBeLessThanOrEqual(100);
      }
    }
  });
});

describe("rankCandidates", () => {
  const fans = buildTasteProfile([rated(90, ["Sci-Fi"]), rated(88, ["Sci-Fi"], 2022)]);

  it("sorts by finalScore descending", () => {
    const result = rankCandidates(fans, fans, [
      candidate({ filmId: "weak", genres: ["Musical"], tmdbRating: 4 }),
      candidate({ filmId: "strong", genres: ["Sci-Fi"], tmdbRating: 8 }),
      candidate({ filmId: "middling", genres: ["Sci-Fi"], tmdbRating: 5 }),
    ]);

    expect(result.ranked.map((c) => c.film.filmId)).toEqual(["strong", "middling", "weak"]);
    expect(result.noMatches).toBe(false);
  });

  it("caps the shortlist at the configured attempt limit", () => {
    const many = Array.from({ length: 10 }, (_, i) =>
      candidate({ filmId: `c${i}`, tmdbRating: 5 + i * 0.4 })
    );
    const result = rankCandidates(fans, fans, many);

    expect(result.shortlist).toHaveLength(ALGORITHM_CONFIG.maxAttemptsBeforeFallback);
  });

  it("reports noMatches instead of suggesting a weak film", () => {
    const mismatched = buildTasteProfile([rated(5, ["Musical"]), rated(2, ["Musical"], 2022)]);
    const result = rankCandidates(mismatched, mismatched, [
      candidate({ filmId: "bad", genres: ["Musical"], tmdbRating: 2 }),
    ]);

    expect(result.noMatches).toBe(true);
    expect(result.shortlist).toHaveLength(0);
    expect(result.ranked[0]!.finalScore).toBeLessThan(ALGORITHM_CONFIG.noMatchThreshold);
  });

  it("reports noMatches for an empty candidate pool rather than throwing", () => {
    const result = rankCandidates(fans, fans, []);
    expect(result.noMatches).toBe(true);
    expect(result.ranked).toHaveLength(0);
  });
});
