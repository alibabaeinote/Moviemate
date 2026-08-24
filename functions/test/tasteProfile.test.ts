import { describe, expect, it } from "vitest";
import { ALGORITHM_CONFIG } from "../src/config/algorithm";
import { buildTasteProfile, decadeKey, predictScore } from "../src/domain/tasteProfile";
import type { RatedFilm, ScorableFilm } from "../src/types";

const film = (over: Partial<RatedFilm> & { filmId: string; score: number }): RatedFilm => ({
  genres: ["Drama"],
  releaseYear: 2021,
  countries: ["US"],
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

  it("builds era and country affinity alongside genre", () => {
    const profile = buildTasteProfile([
      film({ filmId: "1", score: 85, releaseYear: 2022, countries: ["KR"] }),
      film({ filmId: "2", score: 45, releaseYear: 1994, countries: ["US"] }),
    ]);

    expect(profile.eraAffinity["2020s"]).toBe(85);
    expect(profile.eraAffinity["pre-2000"]).toBe(45);
    expect(profile.countryAffinity["KR"]).toBe(85);
    expect(profile.countryAffinity["US"]).toBe(45);
  });

  it("returns an empty profile for a user who has rated nothing", () => {
    const profile = buildTasteProfile([]);
    expect(profile.genreAffinity).toEqual({});
    expect(profile.sampleSize).toBe(0);
  });
});

describe("predictScore", () => {
  const candidate: ScorableFilm = {
    filmId: "c1",
    genres: ["Sci-Fi"],
    releaseYear: 2021,
    countries: ["US"],
    tmdbRating: 8,
  };

  it("applies the configured 0.6 / 0.25 / 0.15 signal weights", () => {
    const profile = buildTasteProfile([
      film({ filmId: "1", score: 100, genres: ["Sci-Fi"], releaseYear: 2021, countries: ["US"] }),
    ]);
    // Every dimension is 100, so the weighted sum is 100 regardless of split.
    expect(predictScore(profile, candidate)).toBeCloseTo(100, 6);
  });

  it("weights genre most heavily", () => {
    const genreOnly = buildTasteProfile([
      film({ filmId: "1", score: 100, genres: ["Sci-Fi"], releaseYear: 1980, countries: ["FR"] }),
    ]);
    const countryOnly = buildTasteProfile([
      film({ filmId: "1", score: 100, genres: ["Horror"], releaseYear: 1980, countries: ["US"] }),
    ]);

    expect(predictScore(genreOnly, candidate)).toBeGreaterThan(
      predictScore(countryOnly, candidate)
    );
  });

  it("falls back to the neutral midpoint for unseen dimensions", () => {
    const profile = buildTasteProfile([]);
    expect(predictScore(profile, candidate)).toBeCloseTo(ALGORITHM_CONFIG.neutralAffinity, 6);
  });

  it("stays inside 0-100 for the extremes of the Taste Dial", () => {
    const hated = buildTasteProfile([
      film({ filmId: "1", score: 0, genres: ["Sci-Fi"], releaseYear: 2021, countries: ["US"] }),
    ]);
    const loved = buildTasteProfile([
      film({ filmId: "1", score: 100, genres: ["Sci-Fi"], releaseYear: 2021, countries: ["US"] }),
    ]);

    expect(predictScore(hated, candidate)).toBeGreaterThanOrEqual(0);
    expect(predictScore(loved, candidate)).toBeLessThanOrEqual(100);
  });
});
