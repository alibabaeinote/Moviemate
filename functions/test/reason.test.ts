import { describe, expect, it } from "vitest";
import { buildReason } from "../src/domain/reason";
import { buildTasteProfile } from "../src/domain/tasteProfile";
import type { RatedFilm, ScorableFilm } from "../src/types";

const rated = (score: number, genres: string[], year = 2021): RatedFilm => ({
  filmId: `r-${score}-${genres.join("-")}-${year}`,
  score,
  genres,
  releaseYear: year,
  countries: ["US"],
  tmdbRating: 7,
});

const film: ScorableFilm = {
  filmId: "c1",
  genres: ["Sci-Fi", "Drama"],
  releaseYear: 2021,
  countries: ["US"],
  tmdbRating: 7.5,
};

describe("buildReason", () => {
  it("names the genre when both users score it above the threshold", () => {
    const profile = buildTasteProfile([rated(90, ["Sci-Fi"]), rated(85, ["Drama"])]);
    expect(buildReason(profile, profile, film)).toContain("Sci-Fi");
  });

  it("uses the weaker of the two affinities, not the average", () => {
    // A adores Sci-Fi, B is indifferent — that is not a shared love.
    const enthusiast = buildTasteProfile([rated(100, ["Sci-Fi"]), rated(100, ["Drama"])]);
    const indifferent = buildTasteProfile([rated(30, ["Sci-Fi"]), rated(30, ["Drama"])]);

    expect(buildReason(enthusiast, indifferent, film)).toBe("A pick that fits both your tastes");
  });

  it("falls back to the era when no genre is shared strongly enough", () => {
    const profile = buildTasteProfile([
      rated(50, ["Sci-Fi"], 2021),
      rated(95, ["Western"], 2022),
    ]);
    expect(buildReason(profile, profile, film)).toBe("You both gravitate toward 2020s films");
  });

  it("falls back to the neutral line when nothing is shared", () => {
    const profile = buildTasteProfile([rated(20, ["Sci-Fi"], 2021)]);
    expect(buildReason(profile, profile, film)).toBe("A pick that fits both your tastes");
  });

  it("never invents a reason for users with no ratings at all", () => {
    const empty = buildTasteProfile([]);
    expect(buildReason(empty, empty, film)).toBe("A pick that fits both your tastes");
  });
});
