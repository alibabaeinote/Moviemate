import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import {
  assertFails,
  assertSucceeds,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  Timestamp,
  addDoc,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
} from "firebase/firestore";
import { ALI, PAIR, SARA, STRANGER, makeTestEnv, seed } from "./helpers";

/**
 * The rest of the access model: who can read a pair, what a client may write to
 * its own user document, and which collections are server-owned.
 */

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

describe("pair membership gates everything underneath", () => {
  it("lets a member read the pair", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertSucceeds(getDoc(doc(db, "pairs", PAIR)));
  });

  it("STOPS a stranger reading the pair", async () => {
    const db = env.authenticatedContext(STRANGER).firestore();
    await assertFails(getDoc(doc(db, "pairs", PAIR)));
  });

  it("STOPS a stranger reading the pair's matches", async () => {
    const db = env.authenticatedContext(STRANGER).firestore();
    await assertFails(getDocs(collection(db, "pairs", PAIR, "matches")));
  });

  it("STOPS a stranger reading the pair's ratings", async () => {
    const db = env.authenticatedContext(STRANGER).firestore();
    await assertFails(getDocs(collection(db, "pairs", PAIR, "ratings")));
  });

  it("STOPS an unauthenticated caller reading anything", async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, "pairs", PAIR)));
  });
});

describe("/pairs is closed to client writes", () => {
  it("STOPS a member editing the pair at all", async () => {
    // Joining goes through the joinPair callable so the invite code and its
    // expiry are checked server-side.
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(updateDoc(doc(db, "pairs", PAIR), { streakCount: 999 }));
  });

  it("STOPS a member forging aBothOnboarded", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(updateDoc(doc(db, "pairs", PAIR), { aBothOnboarded: true }));
  });

  it("STOPS a member deleting the pair", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(deleteDoc(doc(db, "pairs", PAIR)));
  });
});

describe("/users — field whitelist", () => {
  it("lets a user edit their own name and notification settings", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertSucceeds(
      updateDoc(doc(db, "users", ALI), {
        name: "Ali R.",
        notificationSettings: { dailyMatch: false, partnerActivity: true, reminders: true },
      })
    );
  });

  it("lets a user register an FCM token", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertSucceeds(
      updateDoc(doc(db, "users", ALI), {
        fcmTokens: ["token-1"],
        fcmTokenUpdatedAt: Timestamp.now(),
      })
    );
  });

  it("STOPS a user reassigning their own pairId", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(updateDoc(doc(db, "users", ALI), { pairId: "some_other_pair" }));
  });

  it("STOPS a user inflating their own ratingCount", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(updateDoc(doc(db, "users", ALI), { ratingCount: 999 }));
  });

  it("STOPS a user editing their onboarding state", async () => {
    // Ali is seeded as onboarded, so this has to flip the value to be a real
    // change — writing the value it already holds affects no keys at all and is
    // correctly allowed as a no-op.
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(updateDoc(doc(db, "users", ALI), { onboardingComplete: false }));
  });

  it("treats writing an unchanged server-owned value as a harmless no-op", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertSucceeds(updateDoc(doc(db, "users", ALI), { onboardingComplete: true }));

    const snapshot = await getDoc(doc(db, "users", ALI));
    expect(snapshot.data()?.onboardingComplete).toBe(true);
  });

  it("STOPS reading someone else's profile", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(getDoc(doc(db, "users", SARA)));
  });

  it("STOPS deleting an account directly", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(deleteDoc(doc(db, "users", ALI)));
  });
});

describe("matches are produced by the server only", () => {
  it("STOPS a client creating a match", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(
      setDoc(doc(db, "pairs", PAIR, "matches", "forged"), {
        filmId: "1",
        score: 100,
        reason: "trust me",
        suggestedAt: Timestamp.now(),
        status: "suggested",
        attemptNumber: 1,
        commitStatus: { userA: true, userB: true },
        bothConfirmedAt: null,
        watchedConfirmedAt: null,
        shortlist: [],
      })
    );
  });

  it("STOPS a client deleting a match", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(deleteDoc(doc(db, "pairs", PAIR, "matches", "match_1")));
  });
});

describe("filmCache is read-only to clients", () => {
  it("lets any signed-in user read film metadata", async () => {
    const db = env.authenticatedContext(STRANGER).firestore();
    await assertSucceeds(getDoc(doc(db, "filmCache", "438631")));
  });

  it("STOPS an unauthenticated read", async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, "filmCache", "438631")));
  });

  it("STOPS a client writing film metadata", async () => {
    // The 6-month TTL is a TMDB licence obligation; clients must not be able to
    // extend or forge a cache entry.
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(
      updateDoc(doc(db, "filmCache", "438631"), {
        expiresAt: Timestamp.fromMillis(Date.now() + 10 * 365 * 24 * 3600 * 1000),
      })
    );
  });
});

describe("notificationLog is invisible to clients", () => {
  it("STOPS reading it", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(getDocs(collection(db, "notificationLog")));
  });

  it("STOPS writing it", async () => {
    const db = env.authenticatedContext(ALI).firestore();
    await assertFails(addDoc(collection(db, "notificationLog"), { userId: ALI }));
  });
});
