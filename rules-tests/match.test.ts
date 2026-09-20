import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import {
  assertFails,
  assertSucceeds,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { collection, doc, serverTimestamp, setDoc, updateDoc } from "firebase/firestore";
import { ALI, PAIR, SARA, STRANGER, makeTestEnv, seed } from "./helpers";

/**
 * The no-Blaze fallback for the daily match loop (firestore.rules, deviation
 * e): generateDailyMatch and onMatchUpdate are Cloud Functions, so
 * createsTodaysMatch(), updatesLastMatchGeneratedAt() and advancesStreak()
 * let a pair member perform the same writes those would, client-computed
 * (web/match-engine.js) rather than server-verified. commitStatus and
 * watchedConfirmedAt/By need none of this — those paths already existed and
 * are covered by commit.test.ts — so this file only covers what's new.
 */

let env: RulesTestEnvironment;

const matchShape = (overrides: Record<string, unknown> = {}) => ({
  filmId: "999999",
  score: 82,
  reason: "You both love Comedy",
  suggestedAt: serverTimestamp(),
  status: "suggested",
  attemptNumber: 1,
  commitStatus: { userA: false, userB: false },
  bothConfirmedAt: null,
  watchedConfirmedAt: null,
  watchedConfirmedBy: null,
  shortlist: [],
  ...overrides,
});

async function openTheGate() {
  await env.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), "pairs", PAIR), { lastMatchGeneratedAt: null });
  });
}

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

describe("createsTodaysMatch — the no-Blaze fallback for generateDailyMatch", () => {
  it("lets a pair member create today's match once the gate is open", async () => {
    await openTheGate();
    const db = env.authenticatedContext(ALI).firestore();
    const ref = doc(collection(db, "pairs", PAIR, "matches"));
    await assertSucceeds(setDoc(ref, matchShape()));
  });

  it("lets a pair member write a 'no match today' document", async () => {
    await openTheGate();
    const db = env.authenticatedContext(SARA).firestore();
    const ref = doc(collection(db, "pairs", PAIR, "matches"));
    await assertSucceeds(
      setDoc(ref, matchShape({ filmId: "", score: 0, reason: "", status: "dismissed" }))
    );
  });

  it("STOPS creating another match within the 20h gate", async () => {
    // seed() leaves lastMatchGeneratedAt at "now" — the gate is still closed.
    const db = env.authenticatedContext(ALI).firestore();
    const ref = doc(collection(db, "pairs", PAIR, "matches"));
    await assertFails(setDoc(ref, matchShape()));
  });

  it("STOPS a stranger creating a match", async () => {
    await openTheGate();
    const db = env.authenticatedContext(STRANGER).firestore();
    const ref = doc(collection(db, "pairs", PAIR, "matches"));
    await assertFails(setDoc(ref, matchShape()));
  });

  it("STOPS forging a score outside 0-100", async () => {
    await openTheGate();
    const db = env.authenticatedContext(ALI).firestore();
    const ref = doc(collection(db, "pairs", PAIR, "matches"));
    await assertFails(setDoc(ref, matchShape({ score: 150 })));
  });

  it("STOPS pre-filling commitStatus as already committed", async () => {
    await openTheGate();
    const db = env.authenticatedContext(ALI).firestore();
    const ref = doc(collection(db, "pairs", PAIR, "matches"));
    await assertFails(
      setDoc(ref, matchShape({ commitStatus: { userA: true, userB: false } }))
    );
  });

  it("STOPS an invalid status value", async () => {
    await openTheGate();
    const db = env.authenticatedContext(ALI).firestore();
    const ref = doc(collection(db, "pairs", PAIR, "matches"));
    await assertFails(setDoc(ref, matchShape({ status: "watched" })));
  });

  it("STOPS back-dating suggestedAt", async () => {
    await openTheGate();
    const db = env.authenticatedContext(ALI).firestore();
    const ref = doc(collection(db, "pairs", PAIR, "matches"));
    await assertFails(setDoc(ref, matchShape({ suggestedAt: new Date("2020-01-01") })));
  });
});

describe("updatesLastMatchGeneratedAt — the pair-side half of match generation", () => {
  it("lets a pair member stamp it alone", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertSucceeds(
      updateDoc(doc(db, "pairs", PAIR), { lastMatchGeneratedAt: serverTimestamp() })
    );
  });

  it("STOPS bundling other fields into the same write", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(
      updateDoc(doc(db, "pairs", PAIR), {
        lastMatchGeneratedAt: serverTimestamp(),
        streakCount: 99,
      })
    );
  });

  it("STOPS a stranger stamping it", async () => {
    const db = env.authenticatedContext(STRANGER).firestore();
    await assertFails(
      updateDoc(doc(db, "pairs", PAIR), { lastMatchGeneratedAt: serverTimestamp() })
    );
  });
});

describe("advancesStreak — the no-Blaze fallback for onMatchUpdate's updateStreak", () => {
  it("lets a pair member advance the streak by exactly one", async () => {
    // seed() leaves streakCount at 3.
    const db = env.authenticatedContext(ALI).firestore();
    await assertSucceeds(
      updateDoc(doc(db, "pairs", PAIR), { streakCount: 4, lastWatchAt: serverTimestamp() })
    );
  });

  it("lets a pair member reset the streak to one", async () => {
    const db = env.authenticatedContext(SARA).firestore();
    await assertSucceeds(
      updateDoc(doc(db, "pairs", PAIR), { streakCount: 1, lastWatchAt: serverTimestamp() })
    );
  });

  it("STOPS jumping the streak by more than one", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(
      updateDoc(doc(db, "pairs", PAIR), { streakCount: 10, lastWatchAt: serverTimestamp() })
    );
  });

  it("STOPS advancing the streak without lastWatchAt", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(updateDoc(doc(db, "pairs", PAIR), { streakCount: 4 }));
  });

  it("STOPS a stranger advancing the streak", async () => {
    const db = env.authenticatedContext(STRANGER).firestore();
    await assertFails(
      updateDoc(doc(db, "pairs", PAIR), { streakCount: 4, lastWatchAt: serverTimestamp() })
    );
  });
});
