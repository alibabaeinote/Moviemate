import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import {
  assertFails,
  assertSucceeds,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, serverTimestamp, updateDoc } from "firebase/firestore";
import { ALI, PAIR, SARA, STRANGER, makeTestEnv, seed } from "./helpers";

/**
 * Mutual commitment (PRD §7.2) — the rule this whole file exists for.
 *
 * The v1 rules used onlyChangedField(..., "commitStatus.userA"), which could not
 * work: affectedKeys() reports only TOP-LEVEL keys, so editing userA and editing
 * userB both look like a single "commitStatus" change. Either partner could have
 * set both flags and manufactured a mutual commitment alone — exactly what the
 * product says must be impossible.
 *
 * These tests pin the fix.
 */

let env: RulesTestEnvironment;

const matchRef = (db: ReturnType<RulesTestEnvironment["authenticatedContext"]>["firestore"] extends
  () => infer T
  ? T
  : never) => doc(db, "pairs", PAIR, "matches", "match_1");

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

describe("commitStatus — a partner may only commit for themselves", () => {
  it("lets userA raise their own flag", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertSucceeds(
      updateDoc(matchRef(db), { "commitStatus.userA": true })
    );
  });

  it("lets userB raise their own flag", async () => {
    const db = env.authenticatedContext(SARA).firestore();
    await assertSucceeds(
      updateDoc(matchRef(db), { "commitStatus.userB": true })
    );
  });

  it("STOPS userA raising userB's flag", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(
      updateDoc(matchRef(db), { "commitStatus.userB": true })
    );
  });

  it("STOPS userB raising userA's flag", async () => {
    const db = env.authenticatedContext(SARA).firestore();
    await assertFails(
      updateDoc(matchRef(db), { "commitStatus.userA": true })
    );
  });

  it("STOPS one partner setting both flags at once — the whole point", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(
      updateDoc(matchRef(db), {
        commitStatus: { userA: true, userB: true },
      })
    );
  });

  it("STOPS a partner smuggling the other's flag alongside their own", async () => {
    // This is the case the v1 rule let through: one write, both keys, and
    // affectedKeys() sees only "commitStatus".
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(
      updateDoc(matchRef(db), {
        "commitStatus.userA": true,
        "commitStatus.userB": true,
      })
    );
  });

  it("leaves the partner's flag untouched after a legitimate commit", async () => {
    const aliDb = env.authenticatedContext(ALI).firestore();
    await assertSucceeds(updateDoc(matchRef(aliDb), { "commitStatus.userA": true }));

    const snapshot = await getDoc(matchRef(aliDb));
    expect(snapshot.data()?.commitStatus).toEqual({ userA: true, userB: false });
  });

  it("still refuses the partner's flag once one side has committed", async () => {
    const aliDb = env.authenticatedContext(ALI).firestore();
    await assertSucceeds(updateDoc(matchRef(aliDb), { "commitStatus.userA": true }));
    await assertFails(updateDoc(matchRef(aliDb), { "commitStatus.userB": true }));
  });

  it("STOPS a non-member committing at all", async () => {
    const db = env.authenticatedContext(STRANGER).firestore();
    await assertFails(updateDoc(matchRef(db), { "commitStatus.userA": true }));
  });

  it("STOPS an unauthenticated caller committing", async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(updateDoc(matchRef(db), { "commitStatus.userA": true }));
  });
});

describe("server-owned match fields stay server-owned", () => {
  it("STOPS a client stamping bothConfirmedAt", async () => {
    // This is the field the Us screen counts real matches by (PRD §9). If a
    // client could write it, the headline number becomes unreliable.
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(
      updateDoc(matchRef(db), { bothConfirmedAt: serverTimestamp() })
    );
  });

  it("STOPS a client editing the match score", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(updateDoc(matchRef(db), { score: 100 }));
  });

  it("STOPS a client advancing attemptNumber", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(updateDoc(matchRef(db), { attemptNumber: 2 }));
  });

  it("STOPS a client swapping the film", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(updateDoc(matchRef(db), { filmId: "999999" }));
  });

  it("STOPS a client bundling a commit with a score change", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(
      updateDoc(matchRef(db), { "commitStatus.userA": true, score: 100 })
    );
  });
});

describe("watched confirmation is manual and one-way", () => {
  it("lets either member record that they watched it", async () => {
    const db = env.authenticatedContext(SARA).firestore();
    await assertSucceeds(
      updateDoc(matchRef(db), { watchedConfirmedAt: serverTimestamp() })
    );
  });

  it("STOPS a back-dated confirmation", async () => {
    // The rule pins the value to request.time, so a client cannot claim they
    // watched something last week.
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(
      updateDoc(matchRef(db), { watchedConfirmedAt: new Date("2020-01-01") })
    );
  });

  it("STOPS re-confirming something already marked watched", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertSucceeds(
      updateDoc(matchRef(db), { watchedConfirmedAt: serverTimestamp() })
    );
    await assertFails(
      updateDoc(matchRef(db), { watchedConfirmedAt: serverTimestamp() })
    );
  });

  it("STOPS a non-member confirming", async () => {
    const db = env.authenticatedContext(STRANGER).firestore();
    await assertFails(
      updateDoc(matchRef(db), { watchedConfirmedAt: serverTimestamp() })
    );
  });
});
