import { afterAll, beforeEach, describe, expect, it } from "vitest";
import { Timestamp } from "firebase-admin/firestore";
import {
  ALI,
  FILM,
  PAIR,
  SARA,
  changeFor,
  clearAll,
  db,
  fft,
  matchDoc,
  matchPath,
  seed,
  watchlistDoc,
  watchlistPath,
} from "./helpers";
import { onMatchUpdate } from "../src/triggers/onMatchUpdate";

/**
 * The match lifecycle, run for real.
 *
 * All three transitions this trigger handles write back to the SAME match
 * document, so each one re-fires the trigger. The guards that stop that are the
 * main thing under test here — a wrong guard is an infinite loop discovered in
 * production on a Firestore bill, not in review.
 */

const wrapped = fft.wrap(onMatchUpdate);

const params = { pairId: PAIR, matchId: "match_1" };

async function fire(
  before: Record<string, unknown>,
  after: Record<string, unknown>
): Promise<void> {
  await wrapped({ data: changeFor(matchPath("match_1"), before, after), params } as never);
}

async function readMatch() {
  const snapshot = await db().doc(matchPath("match_1")).get();
  return snapshot.data();
}

async function readPair() {
  const snapshot = await db().doc(`pairs/${PAIR}`).get();
  return snapshot.data();
}

async function watchlistFor(filmId: string) {
  const snapshot = await db()
    .collection(`pairs/${PAIR}/watchlist`)
    .where("filmId", "==", filmId)
    .get();
  return snapshot.docs.map((d) => ({ id: d.id, ...d.data() }));
}

beforeEach(async () => {
  await clearAll();
  await seed();
});

afterAll(() => {
  fft.cleanup();
});

describe("mutual commitment", () => {
  it("stamps bothConfirmedAt when the second partner commits", async () => {
    const before = matchDoc({ commitStatus: { userA: true, userB: false } });
    const after = matchDoc({ commitStatus: { userA: true, userB: true } });
    await db().doc(matchPath("match_1")).set(after);

    await fire(before, after);

    const match = await readMatch();
    expect(match?.bothConfirmedAt).toBeTruthy();
  });

  it("puts the agreed film on the shared watchlist as ready", async () => {
    // A film both of them said yes to belongs on the list, not only on today's
    // card — otherwise "Ready to watch" only ever fills from manual adds.
    const before = matchDoc({ commitStatus: { userA: true, userB: false } });
    const after = matchDoc({ commitStatus: { userA: true, userB: true } });
    await db().doc(matchPath("match_1")).set(after);

    await fire(before, after);

    const items = await watchlistFor(FILM);
    expect(items).toHaveLength(1);
    expect(items[0]).toMatchObject({ status: "ready", source: "match" });
  });

  it("does nothing when only one partner has committed", async () => {
    const before = matchDoc();
    const after = matchDoc({ commitStatus: { userA: true, userB: false } });
    await db().doc(matchPath("match_1")).set(after);

    await fire(before, after);

    const match = await readMatch();
    expect(match?.bothConfirmedAt).toBeNull();
    expect(await watchlistFor(FILM)).toHaveLength(0);
  });

  it("does not re-stamp or duplicate when it fires again on its own write", async () => {
    // This is the loop guard. The handler writes bothConfirmedAt, which fires
    // the trigger a second time with both flags already true.
    const before = matchDoc({ commitStatus: { userA: true, userB: false } });
    const after = matchDoc({ commitStatus: { userA: true, userB: true } });
    await db().doc(matchPath("match_1")).set(after);

    await fire(before, after);
    const firstStamp = (await readMatch())?.bothConfirmedAt;

    // Re-fire with both flags true on BOTH sides, as the self-write would.
    const stamped = (await readMatch()) as Record<string, unknown>;
    await fire(stamped, stamped);

    const match = await readMatch();
    expect(match?.bothConfirmedAt).toEqual(firstStamp);
    expect(await watchlistFor(FILM)).toHaveLength(1);
  });
});

describe("watched cycle", () => {
  const watchedAt = Timestamp.fromDate(new Date("2026-09-03T21:00:00Z"));

  async function confirmWatched(over: Record<string, unknown> = {}) {
    const before = matchDoc({
      commitStatus: { userA: true, userB: true },
      bothConfirmedAt: Timestamp.now(),
      ...over,
    });
    const after = { ...before, watchedConfirmedAt: watchedAt };
    await db().doc(matchPath("match_1")).set(after);
    await fire(before, after);
  }

  it("marks the match watched", async () => {
    await confirmWatched();
    expect((await readMatch())?.status).toBe("watched");
  });

  it("mirrors watched onto the pair's watchlist entry", async () => {
    await db().doc(watchlistPath("item_1")).set(
      watchlistDoc({ status: "ready", commitStatus: { userA: true, userB: true } })
    );

    await confirmWatched();

    const items = await watchlistFor(FILM);
    expect(items[0]).toMatchObject({ status: "watched" });
    expect(items[0]).toHaveProperty("watchedAt");
  });

  it("starts the streak at 1 on the pair's first watch", async () => {
    await confirmWatched();
    const pair = await readPair();
    expect(pair?.streakCount).toBe(1);
    expect(pair?.lastWatchAt).toBeTruthy();
  });

  it("advances the streak on a later day", async () => {
    await clearAll();
    await seed({
      streakCount: 4,
      lastWatchAt: Timestamp.fromDate(new Date("2026-09-02T20:00:00Z")),
    });

    await confirmWatched();

    expect((await readPair())?.streakCount).toBe(5);
  });

  it("does not advance the streak twice on the same local day", async () => {
    await clearAll();
    await seed({
      streakCount: 3,
      lastWatchAt: Timestamp.fromDate(new Date("2026-09-03T18:00:00Z")),
    });

    await confirmWatched();

    // Same calendar day as lastWatchAt — a second film tonight is lovely, but
    // it is not a second day.
    expect((await readPair())?.streakCount).toBe(3);
  });

  it("resets the streak after a lapse beyond the grace window", async () => {
    await clearAll();
    await seed({
      streakCount: 11,
      lastWatchAt: Timestamp.fromDate(new Date("2026-08-20T20:00:00Z")),
    });

    await confirmWatched();

    expect((await readPair())?.streakCount).toBe(1);
  });

  it("does not re-run when it fires again on its own status write", async () => {
    // The handler writes status:"watched", re-firing the trigger with
    // watchedConfirmedAt already set on BOTH sides. If the guard were on the
    // after-state alone, the streak would advance twice.
    await clearAll();
    await seed({
      streakCount: 2,
      lastWatchAt: Timestamp.fromDate(new Date("2026-09-01T20:00:00Z")),
    });

    await confirmWatched();
    expect((await readPair())?.streakCount).toBe(3);

    const stored = (await readMatch()) as Record<string, unknown>;
    await fire(stored, stored);

    expect((await readPair())?.streakCount).toBe(3);
  });
});

describe("guards", () => {
  it("ignores a write that changes nothing relevant", async () => {
    const before = matchDoc();
    const after = matchDoc({ ...before, reason: "A different explanation" });
    await db().doc(matchPath("match_1")).set(after);

    await fire(before, after);

    const match = await readMatch();
    expect(match?.bothConfirmedAt).toBeNull();
    expect(match?.status).toBe("suggested");
    expect((await readPair())?.streakCount).toBe(0);
  });

  it("does not demote a watchlist item that is already watched", async () => {
    await db().doc(watchlistPath("item_1")).set(
      watchlistDoc({ status: "watched", watchedAt: Timestamp.now() })
    );

    const before = matchDoc({ commitStatus: { userA: true, userB: false } });
    const after = matchDoc({ commitStatus: { userA: true, userB: true } });
    await db().doc(matchPath("match_1")).set(after);

    await fire(before, after);

    expect((await watchlistFor(FILM))[0]).toMatchObject({ status: "watched" });
  });
});
