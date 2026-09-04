import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import {
  assertFails,
  assertSucceeds,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { Timestamp, addDoc, collection, deleteDoc, doc, setDoc, updateDoc } from "firebase/firestore";
import { ALI, PAIR, SARA, STRANGER, makeTestEnv, seed } from "./helpers";

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await makeTestEnv();
});
afterAll(async () => {
  await env.cleanup();
});
beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
});

const rating = (userId: string, score: number) => ({
  userId,
  filmId: "438631",
  score,
  isInitialOnboarding: true,
  reactionEmoji: null,
  ratedAt: Timestamp.now(),
});

describe("ratings — the Taste Dial range is enforced by the rules", () => {
  it("accepts a score at the bottom of the dial", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertSucceeds(
      setDoc(doc(db, "pairs", PAIR, "ratings", `${ALI}_438631`), rating(ALI, 0))
    );
  });

  it("accepts a score at the top of the dial", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertSucceeds(
      setDoc(doc(db, "pairs", PAIR, "ratings", `${ALI}_438631`), rating(ALI, 100))
    );
  });

  it("accepts a fractional score — the dial is continuous, not five steps", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertSucceeds(
      setDoc(doc(db, "pairs", PAIR, "ratings", `${ALI}_438631`), rating(ALI, 73.5))
    );
  });

  it("STOPS a score above 100", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(
      setDoc(doc(db, "pairs", PAIR, "ratings", `${ALI}_438631`), rating(ALI, 101))
    );
  });

  it("STOPS a negative score", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(
      setDoc(doc(db, "pairs", PAIR, "ratings", `${ALI}_438631`), rating(ALI, -1))
    );
  });

  it("STOPS a non-numeric score", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(
      setDoc(doc(db, "pairs", PAIR, "ratings", `${ALI}_438631`), {
        ...rating(ALI, 0),
        score: "excellent",
      })
    );
  });
});

describe("ratings belong to their author", () => {
  it("STOPS writing a rating in the partner's name", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(
      setDoc(doc(db, "pairs", PAIR, "ratings", `${SARA}_438631`), rating(SARA, 90))
    );
  });

  it("lets an author re-rate the same film, updating rather than duplicating", async () => {
    // The id is `{uid}_{filmId}`, so a second rating of the same film is an
    // update — the documented "Duplicate Rating" case handled by convention.
    const db = env.authenticatedContext(ALI).firestore();
    const ref = doc(db, "pairs", PAIR, "ratings", `${ALI}_438631`);

    await assertSucceeds(setDoc(ref, rating(ALI, 60)));
    await assertSucceeds(updateDoc(ref, { score: 85 }));
  });

  it("STOPS editing the partner's rating", async () => {
    const aliDb = env.authenticatedContext(ALI).firestore();
    const saraDb = env.authenticatedContext(SARA).firestore();
    const ref = `${SARA}_438631`;

    await assertSucceeds(
      setDoc(doc(saraDb, "pairs", PAIR, "ratings", ref), rating(SARA, 70))
    );
    await assertFails(
      updateDoc(doc(aliDb, "pairs", PAIR, "ratings", ref), { score: 10 })
    );
  });

  it("STOPS an update pushing a score out of range", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    const ref = doc(db, "pairs", PAIR, "ratings", `${ALI}_438631`);

    await assertSucceeds(setDoc(ref, rating(ALI, 60)));
    await assertFails(updateDoc(ref, { score: 150 }));
  });

  it("STOPS a stranger writing a rating", async () => {
    const db = env.authenticatedContext(STRANGER).firestore();
    await assertFails(
      setDoc(doc(db, "pairs", PAIR, "ratings", `${STRANGER}_438631`), rating(STRANGER, 50))
    );
  });

  it("STOPS deleting a rating", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    const ref = doc(db, "pairs", PAIR, "ratings", `${ALI}_438631`);
    await assertSucceeds(setDoc(ref, rating(ALI, 60)));
    await assertFails(deleteDoc(ref));
  });
});

describe("watchlist", () => {
  const item = (addedBy: string, over: Record<string, unknown> = {}) => ({
    filmId: "335984",
    addedBy,
    addedAt: Timestamp.now(),
    source: "manual_search",
    status: "waiting",
    commitStatus: { userA: false, userB: false },
    watchedAt: null,
    mutualScore: null,
    ...over,
  });

  it("lets a member add a film they found", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertSucceeds(addDoc(collection(db, "pairs", PAIR, "watchlist"), item(ALI)));
  });

  it("STOPS adding a film in the partner's name", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(addDoc(collection(db, "pairs", PAIR, "watchlist"), item(SARA)));
  });

  it("STOPS adding something pre-marked as watched", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(
      addDoc(collection(db, "pairs", PAIR, "watchlist"), item(ALI, { status: "watched" }))
    );
  });

  it("STOPS adding something with a mutual score already on it", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(
      addDoc(collection(db, "pairs", PAIR, "watchlist"), item(ALI, { mutualScore: 99 }))
    );
  });

  it("lets the other partner say 'I'm in too' from the list", async () => {
    // item_1 is seeded as added by Sara, with her flag already true.
    const db = env.authenticatedContext(ALI).firestore();
    await assertSucceeds(
      updateDoc(doc(db, "pairs", PAIR, "watchlist", "item_1"), {
        "commitStatus.userA": true,
      })
    );
  });

  it("STOPS committing on the partner's behalf from the list", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(
      updateDoc(doc(db, "pairs", PAIR, "watchlist", "item_1"), {
        "commitStatus.userB": false,
      })
    );
  });

  it("STOPS a client promoting an item to ready", async () => {
    // status is derived by onWatchlistUpdate once both have committed.
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(
      updateDoc(doc(db, "pairs", PAIR, "watchlist", "item_1"), { status: "ready" })
    );
  });

  it("STOPS a client writing a mutual score", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(
      updateDoc(doc(db, "pairs", PAIR, "watchlist", "item_1"), { mutualScore: 100 })
    );
  });

  it("lets either member remove a film from the shared list", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertSucceeds(deleteDoc(doc(db, "pairs", PAIR, "watchlist", "item_1")));
  });

  it("STOPS a stranger touching the list", async () => {
    const db = env.authenticatedContext(STRANGER).firestore();
    await assertFails(addDoc(collection(db, "pairs", PAIR, "watchlist"), item(STRANGER)));
  });
});
