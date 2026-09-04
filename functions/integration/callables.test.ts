import { afterAll, beforeEach, describe, expect, it } from "vitest";
import { Timestamp } from "firebase-admin/firestore";
import {
  ALI,
  FILM,
  FILM_B,
  PAIR,
  SARA,
  clearAll,
  db,
  fft,
  matchDoc,
  matchPath,
  seed,
} from "./helpers";
import { rejectMatch } from "../src/callable/rejectMatch";
import { createPair } from "../src/callable/createPair";
import { joinPair } from "../src/callable/joinPair";

/**
 * The client-callable surface: sequential rejection, and pairing.
 *
 * These run server-side because a client cannot be trusted with the shortlist
 * order, the invite code, or its expiry.
 */

const reject = fft.wrap(rejectMatch);
const create = fft.wrap(createPair);
const join = fft.wrap(joinPair);

const shortlist = [
  { filmId: FILM, score: 98, reason: "You both love Sci-Fi" },
  { filmId: FILM_B, score: 84, reason: "You both gravitate toward 2020s films" },
  { filmId: "third", score: 71, reason: "A pick that fits both your tastes" },
];

const readMatch = async () => (await db().doc(matchPath("match_1")).get()).data();

beforeEach(async () => {
  await clearAll();
});

afterAll(() => {
  fft.cleanup();
});

describe("rejectMatch — one suggestion at a time", () => {
  beforeEach(async () => {
    await seed();
    await db().doc(matchPath("match_1")).set(matchDoc({ shortlist }));
  });

  const call = (uid = ALI) =>
    reject({ data: { pairId: PAIR, matchId: "match_1" }, auth: { uid } } as never);

  it("advances to the second candidate", async () => {
    const result = (await call()) as { exhausted: boolean; attemptNumber: number };

    expect(result).toMatchObject({ exhausted: false, attemptNumber: 2 });
    const match = await readMatch();
    expect(match?.filmId).toBe(FILM_B);
    expect(match?.score).toBe(84);
  });

  it("resets both commit flags, because a new film needs fresh consent", async () => {
    await db()
      .doc(matchPath("match_1"))
      .set(matchDoc({ shortlist, commitStatus: { userA: true, userB: false } }));

    await call();

    expect((await readMatch())?.commitStatus).toEqual({ userA: false, userB: false });
  });

  it("walks the shortlist in order", async () => {
    await call();
    expect((await readMatch())?.filmId).toBe(FILM_B);

    await call();
    expect((await readMatch())?.filmId).toBe("third");
  });

  it("unlocks the 3-up fallback only after all three are rejected", async () => {
    // Showing several options up front re-creates the disagreement the app
    // exists to remove, so the menu is a last resort, not an opener.
    await call();
    await call();
    const result = (await call()) as { exhausted: boolean };

    expect(result.exhausted).toBe(true);
    const match = await readMatch();
    expect(match?.status).toBe("dismissed");
    expect(match?.fallbackUnlocked).toBe(true);
  });

  it("refuses a match that is no longer open", async () => {
    await db().doc(matchPath("match_1")).set(matchDoc({ shortlist, status: "watched" }));
    await expect(call()).rejects.toThrow();
  });

  it("refuses a caller who is not in the pair", async () => {
    await expect(call("uid_stranger")).rejects.toThrow();
  });

  it("refuses an unauthenticated caller", async () => {
    await expect(
      reject({ data: { pairId: PAIR, matchId: "match_1" } } as never)
    ).rejects.toThrow();
  });
});

describe("pairing", () => {
  async function makeSoloUser(uid: string, name: string) {
    await db().doc(`users/${uid}`).set({
      uid,
      name,
      email: `${name}@example.com`,
      emailVerified: true,
      createdAt: Timestamp.now(),
      pairId: null,
      onboardingComplete: false,
      ratingCount: 0,
      fcmTokens: [],
      fcmTokenUpdatedAt: null,
      notificationSettings: { dailyMatch: true, partnerActivity: true, reminders: true },
      timezone: "UTC",
      lastActiveAt: null,
    });
  }

  it("issues an invite code and links it to the creator", async () => {
    await makeSoloUser(ALI, "Ali");

    const result = (await create({
      data: { timezone: "Europe/Berlin" },
      auth: { uid: ALI },
    } as never)) as { pairId: string; inviteCode: string; inviteCodeExpiresAt: number };

    expect(result.inviteCode).toMatch(/^MVMT-[A-Z2-9]{6}$/);
    expect(result.inviteCodeExpiresAt).toBeGreaterThan(Date.now());

    const pair = (await db().doc(`pairs/${result.pairId}`).get()).data();
    expect(pair).toMatchObject({
      userA: ALI,
      userB: null,
      status: "waiting_partner",
      aBothOnboarded: false,
      streakCount: 0,
      timezone: "Europe/Berlin",
    });
    expect((await db().doc(`users/${ALI}`).get()).data()?.pairId).toBe(result.pairId);
  });

  it("omits look-alike characters from the code, so it can be read aloud", async () => {
    await makeSoloUser(ALI, "Ali");
    const result = (await create({ data: {}, auth: { uid: ALI } } as never)) as {
      inviteCode: string;
    };

    expect(result.inviteCode.slice(5)).not.toMatch(/[IO01]/);
  });

  it("lets a second person join with the code", async () => {
    await makeSoloUser(ALI, "Ali");
    await makeSoloUser(SARA, "Sara");

    const created = (await create({ data: {}, auth: { uid: ALI } } as never)) as {
      pairId: string;
      inviteCode: string;
    };

    const joined = (await join({
      data: { inviteCode: created.inviteCode },
      auth: { uid: SARA },
    } as never)) as { pairId: string; partnerUid: string };

    expect(joined).toEqual({ pairId: created.pairId, partnerUid: ALI });

    const pair = (await db().doc(`pairs/${created.pairId}`).get()).data();
    expect(pair?.userB).toBe(SARA);
    expect(pair?.status).toBe("both_rating");
    expect((await db().doc(`users/${SARA}`).get()).data()?.pairId).toBe(created.pairId);
  });

  it("accepts a lowercase code, because people retype what they were sent", async () => {
    await makeSoloUser(ALI, "Ali");
    await makeSoloUser(SARA, "Sara");
    const created = (await create({ data: {}, auth: { uid: ALI } } as never)) as {
      inviteCode: string;
    };

    await expect(
      join({
        data: { inviteCode: created.inviteCode.toLowerCase() },
        auth: { uid: SARA },
      } as never)
    ).resolves.toBeTruthy();
  });

  it("rejects a code nobody issued", async () => {
    await makeSoloUser(SARA, "Sara");
    await expect(
      join({ data: { inviteCode: "MVMT-ZZZZZZ" }, auth: { uid: SARA } } as never)
    ).rejects.toThrow();
  });

  it("rejects your own invite code", async () => {
    await makeSoloUser(ALI, "Ali");
    const created = (await create({ data: {}, auth: { uid: ALI } } as never)) as {
      inviteCode: string;
    };

    await expect(
      join({ data: { inviteCode: created.inviteCode }, auth: { uid: ALI } } as never)
    ).rejects.toThrow();
  });

  it("rejects a code that has already been used", async () => {
    await makeSoloUser(ALI, "Ali");
    await makeSoloUser(SARA, "Sara");
    await makeSoloUser("uid_third", "Third");

    const created = (await create({ data: {}, auth: { uid: ALI } } as never)) as {
      inviteCode: string;
    };
    await join({ data: { inviteCode: created.inviteCode }, auth: { uid: SARA } } as never);

    await expect(
      join({ data: { inviteCode: created.inviteCode }, auth: { uid: "uid_third" } } as never)
    ).rejects.toThrow();
  });

  it("rejects an expired code", async () => {
    await makeSoloUser(ALI, "Ali");
    await makeSoloUser(SARA, "Sara");

    const created = (await create({ data: {}, auth: { uid: ALI } } as never)) as {
      pairId: string;
      inviteCode: string;
    };
    await db()
      .doc(`pairs/${created.pairId}`)
      .update({ inviteCodeExpiresAt: Timestamp.fromMillis(Date.now() - 1000) });

    await expect(
      join({ data: { inviteCode: created.inviteCode }, auth: { uid: SARA } } as never)
    ).rejects.toThrow();
  });

  it("refuses to pair someone who is already paired", async () => {
    await seed();
    await expect(create({ data: {}, auth: { uid: ALI } } as never)).rejects.toThrow();
  });
});
