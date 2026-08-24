import { describe, expect, it } from "vitest";
import { isNewLocalDay, localDayKey, localHourIn } from "../src/lib/time";

// 2026-08-24T21:30:00Z
const instant = new Date("2026-08-24T21:30:00Z");

describe("localHourIn", () => {
  it("converts UTC into the pair's local hour", () => {
    expect(localHourIn("UTC", instant)).toBe(21);
    expect(localHourIn("Europe/Berlin", instant)).toBe(23); // UTC+2 in August
    expect(localHourIn("America/Los_Angeles", instant)).toBe(14); // UTC-7 in August
  });

  it("rolls past midnight into the next local day", () => {
    expect(localHourIn("Asia/Tokyo", instant)).toBe(6); // next calendar day
    expect(localDayKey("Asia/Tokyo", instant)).toBe("2026-08-25");
    expect(localDayKey("UTC", instant)).toBe("2026-08-24");
  });

  it("falls back to UTC on an unknown timezone instead of throwing", () => {
    expect(localHourIn("Not/AZone", instant)).toBe(21);
    expect(localDayKey("Not/AZone", instant)).toBe("2026-08-24");
  });
});

describe("isNewLocalDay", () => {
  it("treats a never-run pair as due", () => {
    expect(isNewLocalDay("UTC", null, instant)).toBe(true);
  });

  it("is false for an earlier hour on the same local day", () => {
    const earlier = new Date("2026-08-24T07:00:00Z");
    expect(isNewLocalDay("UTC", earlier, instant)).toBe(false);
  });

  it("is true once the local day has rolled over", () => {
    const yesterday = new Date("2026-08-23T21:00:00Z");
    expect(isNewLocalDay("UTC", yesterday, instant)).toBe(true);
  });

  it("uses the pair's local day, not UTC's", () => {
    // Both instants land on 2026-08-24 in UTC, but Tokyo (UTC+9) has already
    // rolled into the 25th by `instant` — so the same pair of timestamps means
    // "same day" in one zone and "new day" in the other.
    const earlierSameUtcDay = new Date("2026-08-24T13:00:00Z");
    expect(localDayKey("UTC", earlierSameUtcDay)).toBe("2026-08-24");
    expect(localDayKey("Asia/Tokyo", earlierSameUtcDay)).toBe("2026-08-24");
    expect(localDayKey("Asia/Tokyo", instant)).toBe("2026-08-25");

    expect(isNewLocalDay("UTC", earlierSameUtcDay, instant)).toBe(false);
    expect(isNewLocalDay("Asia/Tokyo", earlierSameUtcDay, instant)).toBe(true);
  });
});
