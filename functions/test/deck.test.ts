import { describe, expect, it } from "vitest";
import { interleave, quotasFor } from "../src/domain/deck";

describe("interleave", () => {
  it("round-robins the buckets so no era opens the deck in a block", () => {
    const result = interleave([
      ["a1", "a2", "a3"],
      ["b1", "b2", "b3"],
      ["c1", "c2", "c3"],
    ]);

    expect(result).toEqual(["a1", "b1", "c1", "a2", "b2", "c2", "a3", "b3", "c3"]);
  });

  it("keeps going when a bucket runs out early", () => {
    const result = interleave([["a1", "a2", "a3"], ["b1"], ["c1", "c2"]]);

    expect(result).toEqual(["a1", "b1", "c1", "a2", "c2", "a3"]);
  });

  it("ignores empty buckets", () => {
    expect(interleave([[], ["b1", "b2"], []])).toEqual(["b1", "b2"]);
  });

  it("returns nothing for no buckets at all", () => {
    expect(interleave<string>([])).toEqual([]);
    expect(interleave<string>([[], []])).toEqual([]);
  });

  it("loses no items", () => {
    const buckets = [["a1", "a2"], ["b1"], ["c1", "c2", "c3"]];
    expect(interleave(buckets)).toHaveLength(6);
  });
});

describe("quotasFor", () => {
  it("splits a deck across the era shares", () => {
    expect(quotasFor(15, [0.4, 0.3, 0.15, 0.15])).toEqual([6, 5, 2, 2]);
  });

  it("never drops an era to zero, even on a small deck", () => {
    // Without the floor, the two 15% windows would round to 0 and those eras
    // would vanish from the profile entirely.
    const quotas = quotasFor(5, [0.4, 0.3, 0.15, 0.15]);

    expect(quotas.every((q) => q >= 1)).toBe(true);
    expect(quotas).toEqual([2, 2, 1, 1]);
  });
});
