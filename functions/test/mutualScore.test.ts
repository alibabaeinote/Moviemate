import { describe, expect, it } from "vitest";
import { computeMutualScore, scoreDivergence } from "../src/domain/mutualScore";

describe("computeMutualScore", () => {
  it("averages both Taste Dial scores", () => {
    expect(computeMutualScore({ a: 80, b: 60 })).toBe(70);
  });

  it("stays null until both have rated", () => {
    expect(computeMutualScore({ a: 80 })).toBeNull();
    expect(computeMutualScore({ b: 60 })).toBeNull();
    expect(computeMutualScore({})).toBeNull();
  });

  it("treats a genuine zero as a rating, not a missing one", () => {
    expect(computeMutualScore({ a: 0, b: 40 })).toBe(20);
  });

  it("rounds to one decimal so sorting is stable", () => {
    expect(computeMutualScore({ a: 71, b: 72 })).toBe(71.5);
    expect(computeMutualScore({ a: 33, b: 34 })).toBe(33.5);
  });

  it("handles both extremes of the dial", () => {
    expect(computeMutualScore({ a: 0, b: 0 })).toBe(0);
    expect(computeMutualScore({ a: 100, b: 100 })).toBe(100);
  });
});

describe("scoreDivergence", () => {
  it("reports how far apart the two of them landed", () => {
    expect(scoreDivergence({ a: 90, b: 40 })).toBe(50);
    expect(scoreDivergence({ a: 40, b: 90 })).toBe(50);
    expect(scoreDivergence({ a: 70, b: 70 })).toBe(0);
  });

  it("is null until both have rated", () => {
    expect(scoreDivergence({ a: 90 })).toBeNull();
  });
});
