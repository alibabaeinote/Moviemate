import { afterAll, beforeEach, describe, expect, it } from "vitest";
import { Timestamp } from "firebase-admin/firestore";
import {
  FILM,
  PAIR,
  changeFor,
  clearAll,
  db,
  fft,
  seed,
  watchlistDoc,
  watchlistPath,
} from "./helpers";
import { onWatchlistUpdate } from "../src/triggers/onWatchlistUpdate";

/**
 * The three-way Watchlist split: an item becomes "ready" only when both
 * partners are in, mirroring the same mutual-commitment rule the match card
 * uses.
 */

const wrapped = fft.wrap(onWatchlistUpdate);

async function fire(before: Record<string, unknown>, after: Record<string, unknown>) {
  await wrapped({
    data: changeFor(watchlistPath("item_1"), before, after),
    params: { pairId: PAIR, itemId: "item_1" },
  } as never);
}

const readItem = async () => (await db().doc(watchlistPath("item_1")).get()).data();

beforeEach(async () => {
  await clearAll();
  await seed();
});

afterAll(() => {
  fft.cleanup();
});

describe("onWatchlistUpdate", () => {
  it("promotes to ready when the second partner says 'I'm in too'", async () => {
    const before = watchlistDoc({ commitStatus: { userA: false, userB: true } });
    const after = watchlistDoc({ commitStatus: { userA: true, userB: true } });
    await db().doc(watchlistPath("item_1")).set(after);

    await fire(before, after);

    expect((await readItem())?.status).toBe("ready");
  });

  it("leaves an item waiting while only one partner is in", async () => {
    const before = watchlistDoc();
    const after = watchlistDoc({ commitStatus: { userA: false, userB: true } });
    await db().doc(watchlistPath("item_1")).set(after);

    await fire(before, after);

    expect((await readItem())?.status).toBe("waiting");
  });

  it("does not re-run when it fires again on its own status write", async () => {
    const before = watchlistDoc({ commitStatus: { userA: false, userB: true } });
    const after = watchlistDoc({ commitStatus: { userA: true, userB: true } });
    await db().doc(watchlistPath("item_1")).set(after);

    await fire(before, after);
    const stored = (await readItem()) as Record<string, unknown>;

    // Both sides already committed — the guard is on the edge, not the state.
    await expect(fire(stored, stored)).resolves.not.toThrow();
    expect((await readItem())?.status).toBe("ready");
  });

  it("never sends a watched film back to ready", async () => {
    const before = watchlistDoc({
      status: "watched",
      watchedAt: Timestamp.now(),
      commitStatus: { userA: false, userB: true },
    });
    const after = { ...before, commitStatus: { userA: true, userB: true } };
    await db().doc(watchlistPath("item_1")).set(after);

    await fire(before, after);

    expect((await readItem())?.status).toBe("watched");
  });

  it("ignores an edit that does not change commitment", async () => {
    const before = watchlistDoc({ commitStatus: { userA: true, userB: true }, status: "ready" });
    const after = { ...before, filmId: FILM };
    await db().doc(watchlistPath("item_1")).set(after);

    await expect(fire(before, after)).resolves.not.toThrow();
    expect((await readItem())?.status).toBe("ready");
  });
});
