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
  seed,
  watchlistDoc,
  watchlistPath,
} from "./helpers";
import { onRatingComplete } from "../src/triggers/onRatingComplete";

/**
 * Onboarding progress and the shared verdict.
 *
 * Both live in this one trigger because both key off a rating write, but they
 * are otherwise unrelated paths — onboarding counts, post-watch computes.
 */

const wrapped = fft.wrap(onRatingComplete);

const ratingPath = (uid: string, filmId: string) =>
  `pairs/${PAIR}/ratings/${uid}_${filmId}`;

function ratingDoc(uid: string, filmId: string, score: number, onboarding: boolean) {
  return {
    userId: uid,
    filmId,
    score,
    isInitialOnboarding: onboarding,
    reactionEmoji: null,
    ratedAt: Timestamp.now(),
  };
}

/** Write a rating for real, then fire the trigger the write would have fired. */
async function rate(uid: string, filmId: string, score: number, onboarding: boolean) {
  const path = ratingPath(uid, filmId);
  const doc = ratingDoc(uid, filmId, score, onboarding);
  const existing = await db().doc(path).get();
  const before = existing.exists ? existing.data() : undefined;

  await db().doc(path).set(doc);

  await wrapped({
    data: changeFor(path, before, doc),
    params: { pairId: PAIR, ratingId: `${uid}_${filmId}` },
  } as never);
}

/** Fill in n onboarding ratings for a user without firing the trigger each time. */
async function backfillOnboarding(uid: string, count: number) {
  const batch = db().batch();
  for (let i = 0; i < count; i += 1) {
    const filmId = `film_${uid}_${i}`;
    batch.set(db().doc(ratingPath(uid, filmId)), ratingDoc(uid, filmId, 70, true));
  }
  await batch.commit();
}

const readUser = async (uid: string) => (await db().doc(`users/${uid}`).get()).data();
const readPair = async () => (await db().doc(`pairs/${PAIR}`).get()).data();

beforeEach(async () => {
  await clearAll();
});

afterAll(() => {
  fft.cleanup();
});

describe("onboarding progress", () => {
  it("counts ratings onto the user", async () => {
    await seed({ aBothOnboarded: false, onboardingComplete: { ali: false, sara: false } });

    await rate(ALI, "film_x", 80, true);

    expect((await readUser(ALI))?.ratingCount).toBe(1);
    expect((await readUser(ALI))?.onboardingComplete).toBe(false);
  });

  it("marks a user onboarded once they reach the target", async () => {
    await seed({ aBothOnboarded: false, onboardingComplete: { ali: false, sara: false } });
    await backfillOnboarding(ALI, 9);

    await rate(ALI, "film_final", 80, true);

    const user = await readUser(ALI);
    expect(user?.ratingCount).toBe(10);
    expect(user?.onboardingComplete).toBe(true);
  });

  it("does NOT flip the pair while the partner is still rating", async () => {
    await seed({ aBothOnboarded: false, onboardingComplete: { ali: false, sara: false } });
    await backfillOnboarding(ALI, 9);

    await rate(ALI, "film_final", 80, true);

    expect((await readPair())?.aBothOnboarded).toBe(false);
  });

  it("flips the pair once the second partner finishes", async () => {
    // aBothOnboarded is the flag generateDailyMatch waits on, and it is
    // computed server-side precisely so two simultaneous final ratings cannot
    // race each other.
    await seed({ aBothOnboarded: false, onboardingComplete: { ali: true, sara: false } });
    await backfillOnboarding(SARA, 9);

    await rate(SARA, "film_final", 75, true);

    const pair = await readPair();
    expect(pair?.aBothOnboarded).toBe(true);
    expect(pair?.status).toBe("active");
  });

  it("counts only onboarding ratings toward completion", async () => {
    await seed({ aBothOnboarded: false, onboardingComplete: { ali: false, sara: false } });

    // Ten post-watch ratings are ten ratings, but they are not onboarding.
    for (let i = 0; i < 10; i += 1) {
      await db()
        .doc(ratingPath(ALI, `post_${i}`))
        .set(ratingDoc(ALI, `post_${i}`, 60, false));
    }
    await rate(ALI, "post_final", 60, false);

    const user = await readUser(ALI);
    expect(user?.ratingCount).toBe(11);
    expect(user?.onboardingComplete).toBe(false);
  });
});

describe("mutual score", () => {
  beforeEach(async () => {
    await seed();
    await db().doc(watchlistPath("item_1")).set(
      watchlistDoc({ status: "ready", commitStatus: { userA: true, userB: true } })
    );
  });

  const readItem = async () => (await db().doc(watchlistPath("item_1")).get()).data();

  it("stays null after only one partner has rated", async () => {
    // One person's score is an opinion, not a shared verdict.
    await rate(ALI, FILM, 80, false);

    expect((await readItem())?.mutualScore).toBeNull();
  });

  it("averages both scores once the second lands", async () => {
    await rate(ALI, FILM, 80, false);
    await rate(SARA, FILM, 60, false);

    const item = await readItem();
    expect(item?.mutualScore).toBe(70);
    expect(item?.status).toBe("watched");
  });

  it("recomputes when someone changes their mind", async () => {
    await rate(ALI, FILM, 80, false);
    await rate(SARA, FILM, 60, false);
    expect((await readItem())?.mutualScore).toBe(70);

    await rate(ALI, FILM, 100, false);

    expect((await readItem())?.mutualScore).toBe(80);
  });

  it("ignores onboarding ratings of the same film", async () => {
    // Rating Dune during onboarding is not a verdict on watching it together.
    await rate(ALI, FILM, 90, true);
    await rate(SARA, FILM, 90, true);

    expect((await readItem())?.mutualScore).toBeNull();
  });

  it("does nothing for a film that is not on the watchlist", async () => {
    await expect(rate(ALI, "some_other_film", 70, false)).resolves.not.toThrow();
    expect((await readItem())?.mutualScore).toBeNull();
  });

  it("treats a zero as a real rating, not a missing one", async () => {
    await rate(ALI, FILM, 0, false);
    await rate(SARA, FILM, 40, false);

    expect((await readItem())?.mutualScore).toBe(20);
  });
});
