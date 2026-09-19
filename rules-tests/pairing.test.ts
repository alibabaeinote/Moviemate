import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import {
  assertFails,
  assertSucceeds,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  Timestamp,
  deleteDoc,
  doc,
  getDoc,
  serverTimestamp,
  setDoc,
  updateDoc,
} from "firebase/firestore";
import { ALI, SARA, STRANGER, makeTestEnv, seedUnpaired } from "./helpers";

/**
 * The no-Blaze fallback (firestore.rules, deviation d): createPair/joinPair
 * are Cloud Functions, and 2nd-gen functions require the Blaze plan to
 * deploy at all. claimsOwnPair() and joinsOpenSeat() let a client perform
 * the same two writes those callables' Admin-SDK transactions would, and
 * /inviteCodes lets a joiner resolve a shared code to a pairId before
 * they're a member of it. These tests pin that the fallback is exactly as
 * narrow as the comments in firestore.rules claim.
 */

let env: RulesTestEnvironment;
const HOUR = 60 * 60 * 1000;

const openPairShape = (overrides: Record<string, unknown> = {}) => ({
  userA: ALI,
  userB: null,
  inviteCode: "MVMT-XYZ999",
  inviteCodeExpiresAt: Timestamp.fromMillis(Date.now() + 168 * HOUR),
  status: "waiting_partner",
  createdAt: Timestamp.now(),
  aBothOnboarded: false,
  streakCount: 0,
  lastMatchGeneratedAt: null,
  lastWatchAt: null,
  timezone: "UTC",
  ...overrides,
});

const joinFields = (uid: string, overrides: Record<string, unknown> = {}) => ({
  userB: uid,
  status: "both_rating",
  joinedAt: serverTimestamp(),
  userBName: "Sara",
  userBAvatarUrl: null,
  ...overrides,
});

async function seedPair(id: string, overrides: Record<string, unknown> = {}) {
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "pairs", id), openPairShape(overrides));
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
  await seedUnpaired(env);
});

describe("/pairs create — the no-Blaze fallback for createPair", () => {
  it("lets a signed-in user create their own pair", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertSucceeds(setDoc(doc(db, "pairs", "pair_new"), openPairShape()));
  });

  it("STOPS registering someone else as userA", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(setDoc(doc(db, "pairs", "pair_new"), openPairShape({ userA: SARA })));
  });

  it("STOPS pre-filling a partner", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(setDoc(doc(db, "pairs", "pair_new"), openPairShape({ userB: SARA })));
  });

  it("STOPS pre-filling server-owned progress", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(setDoc(doc(db, "pairs", "pair_new"), openPairShape({ streakCount: 5 })));
  });

  it("STOPS creating with the wrong status", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(setDoc(doc(db, "pairs", "pair_new"), openPairShape({ status: "active" })));
  });

  it("STOPS creating with an already-expired invite", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(
      setDoc(
        doc(db, "pairs", "pair_new"),
        openPairShape({ inviteCodeExpiresAt: Timestamp.fromMillis(Date.now() - HOUR) })
      )
    );
  });

  it("STOPS an unauthenticated caller creating a pair", async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(setDoc(doc(db, "pairs", "pair_new"), openPairShape()));
  });
});

describe("claimsOwnPair — the no-Blaze fallback for users/{uid}.pairId", () => {
  it("lets the creator claim the pair they just made", async () => {
    await seedPair("pair_new");
    const db = env.authenticatedContext(ALI).firestore();
    await assertSucceeds(updateDoc(doc(db, "users", ALI), { pairId: "pair_new" }));
  });

  it("lets a joiner claim the pair after taking the open seat", async () => {
    await seedPair("pair_new");
    const saraDb = env.authenticatedContext(SARA).firestore();
    await assertSucceeds(updateDoc(doc(saraDb, "pairs", "pair_new"), joinFields(SARA)));
    await assertSucceeds(updateDoc(doc(saraDb, "users", SARA), { pairId: "pair_new" }));
  });

  it("STOPS claiming a pair that doesn't list you", async () => {
    await seedPair("pair_new");
    const db = env.authenticatedContext(STRANGER).firestore();
    await assertFails(updateDoc(doc(db, "users", STRANGER), { pairId: "pair_new" }));
  });

  it("STOPS claiming a pair that doesn't exist", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(updateDoc(doc(db, "users", ALI), { pairId: "nonexistent" }));
  });

  it("STOPS reclaiming once a pairId is already set", async () => {
    await seedPair("pair_new");
    await seedPair("pair_other", { userA: ALI });
    const db = env.authenticatedContext(ALI).firestore();
    await assertSucceeds(updateDoc(doc(db, "users", ALI), { pairId: "pair_new" }));
    await assertFails(updateDoc(doc(db, "users", ALI), { pairId: "pair_other" }));
  });

  it("STOPS bundling the pairId claim with another field", async () => {
    await seedPair("pair_new");
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(
      updateDoc(doc(db, "users", ALI), { pairId: "pair_new", name: "Sneaky" })
    );
  });
});

describe("joinsOpenSeat — the no-Blaze fallback for joinPair", () => {
  it("lets a stranger claim the open seat", async () => {
    await seedPair("pair_new");
    const db = env.authenticatedContext(SARA).firestore();
    await assertSucceeds(updateDoc(doc(db, "pairs", "pair_new"), joinFields(SARA)));
  });

  it("STOPS joining a seat that's already taken", async () => {
    await seedPair("pair_new", { userB: STRANGER, status: "both_rating" });
    const db = env.authenticatedContext(SARA).firestore();
    await assertFails(updateDoc(doc(db, "pairs", "pair_new"), joinFields(SARA)));
  });

  it("STOPS joining an expired invite", async () => {
    await seedPair("pair_new", {
      inviteCodeExpiresAt: Timestamp.fromMillis(Date.now() - HOUR),
    });
    const db = env.authenticatedContext(SARA).firestore();
    await assertFails(updateDoc(doc(db, "pairs", "pair_new"), joinFields(SARA)));
  });

  it("STOPS joining your own pair", async () => {
    await seedPair("pair_new");
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(updateDoc(doc(db, "pairs", "pair_new"), joinFields(ALI)));
  });

  it("STOPS claiming the seat as someone else", async () => {
    await seedPair("pair_new");
    const db = env.authenticatedContext(SARA).firestore();
    await assertFails(updateDoc(doc(db, "pairs", "pair_new"), joinFields(STRANGER)));
  });

  it("STOPS smuggling a server-owned field into the join write", async () => {
    await seedPair("pair_new");
    const db = env.authenticatedContext(SARA).firestore();
    await assertFails(
      updateDoc(doc(db, "pairs", "pair_new"), joinFields(SARA, { streakCount: 99 }))
    );
  });

  it("STOPS back-dating joinedAt", async () => {
    await seedPair("pair_new");
    const db = env.authenticatedContext(SARA).firestore();
    await assertFails(
      updateDoc(
        doc(db, "pairs", "pair_new"),
        joinFields(SARA, { joinedAt: new Date("2020-01-01") })
      )
    );
  });

  it("STOPS an unauthenticated caller joining", async () => {
    await seedPair("pair_new");
    const db = env.unauthenticatedContext().firestore();
    await assertFails(updateDoc(doc(db, "pairs", "pair_new"), joinFields(SARA)));
  });
});

describe("/inviteCodes — resolving a shared code before joining", () => {
  const codeDoc = (overrides: Record<string, unknown> = {}) => ({
    pairId: "pair_new",
    expiresAt: Timestamp.fromMillis(Date.now() + 168 * HOUR),
    ...overrides,
  });

  it("lets a signed-in user register a code", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertSucceeds(setDoc(doc(db, "inviteCodes", "ABC123"), codeDoc()));
  });

  it("lets any signed-in user read a code, even a stranger to the pair", async () => {
    await env.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "inviteCodes", "ABC123"), codeDoc());
    });
    const db = env.authenticatedContext(STRANGER).firestore();
    await assertSucceeds(getDoc(doc(db, "inviteCodes", "ABC123")));
  });

  it("STOPS an unauthenticated read", async () => {
    await env.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "inviteCodes", "ABC123"), codeDoc());
    });
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, "inviteCodes", "ABC123")));
  });

  it("STOPS registering an already-expired code", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(
      setDoc(
        doc(db, "inviteCodes", "ABC123"),
        codeDoc({ expiresAt: Timestamp.fromMillis(Date.now() - HOUR) })
      )
    );
  });

  it("STOPS registering a code with no string pairId", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(setDoc(doc(db, "inviteCodes", "ABC123"), codeDoc({ pairId: 12345 })));
  });

  it("STOPS updating a registered code", async () => {
    await env.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "inviteCodes", "ABC123"), codeDoc());
    });
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(updateDoc(doc(db, "inviteCodes", "ABC123"), { pairId: "pair_other" }));
  });

  it("STOPS deleting a registered code", async () => {
    await env.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "inviteCodes", "ABC123"), codeDoc());
    });
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(deleteDoc(doc(db, "inviteCodes", "ABC123")));
  });
});

describe("full flow: create, share, join — no Cloud Functions involved", () => {
  it("takes two users from unpaired to paired using only client writes", async () => {
    const aliDb = env.authenticatedContext(ALI).firestore();
    const saraDb = env.authenticatedContext(SARA).firestore();

    await assertSucceeds(setDoc(doc(aliDb, "pairs", "pair_new"), openPairShape()));
    await assertSucceeds(
      setDoc(doc(aliDb, "inviteCodes", "MVMT-XYZ999"), {
        pairId: "pair_new",
        expiresAt: Timestamp.fromMillis(Date.now() + 168 * HOUR),
      })
    );
    await assertSucceeds(updateDoc(doc(aliDb, "users", ALI), { pairId: "pair_new" }));

    const codeSnapshot = await getDoc(doc(saraDb, "inviteCodes", "MVMT-XYZ999"));
    const resolvedPairId = codeSnapshot.data()?.pairId as string;
    expect(resolvedPairId).toBe("pair_new");

    await assertSucceeds(
      updateDoc(doc(saraDb, "pairs", resolvedPairId), joinFields(SARA))
    );
    await assertSucceeds(updateDoc(doc(saraDb, "users", SARA), { pairId: resolvedPairId }));

    const pairSnapshot = await getDoc(doc(aliDb, "pairs", "pair_new"));
    expect(pairSnapshot.data()).toMatchObject({
      userA: ALI,
      userB: SARA,
      status: "both_rating",
    });

    const aliUser = await getDoc(doc(aliDb, "users", ALI));
    const saraUser = await getDoc(doc(saraDb, "users", SARA));
    expect(aliUser.data()?.pairId).toBe("pair_new");
    expect(saraUser.data()?.pairId).toBe("pair_new");
  });
});
