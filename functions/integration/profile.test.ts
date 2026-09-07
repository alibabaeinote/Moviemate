import { afterAll, beforeEach, describe, expect, it } from "vitest";
import {
  ALI,
  PAIR,
  SARA,
  changeFor,
  clearAll,
  db,
  fft,
  pairPath,
  seed,
  userPath,
} from "./helpers";
import { onUserProfileUpdated } from "../src/triggers/onUserProfileUpdated";

/**
 * The only path by which a partner's name or picture becomes visible at all.
 *
 * The security rules let a user read only their own users/{uid} document, so
 * without this trigger copying name/avatarUrl onto the shared pair document,
 * the Us screen would have no way to show anything about the other person.
 */

const wrapped = fft.wrap(onUserProfileUpdated);

async function fire(uid: string, before: Record<string, unknown>, after: Record<string, unknown>) {
  await wrapped({
    data: changeFor(userPath(uid), before, after),
    params: { uid },
  } as never);
}

const readPair = async () => (await db().doc(pairPath()).get()).data();

beforeEach(async () => {
  await clearAll();
  await seed();
});

afterAll(() => {
  fft.cleanup();
});

describe("onUserProfileUpdated", () => {
  it("copies a name change onto userA's side of the pair", async () => {
    await fire(
      ALI,
      { name: "Ali", avatarUrl: null, pairId: PAIR },
      { name: "Alireza", avatarUrl: null, pairId: PAIR }
    );

    await expect(readPair()).resolves.toMatchObject({ userAName: "Alireza" });
  });

  it("copies an avatar change onto userB's side, by seat rather than by chance", async () => {
    await fire(
      SARA,
      { name: "Sara", avatarUrl: null, pairId: PAIR },
      { name: "Sara", avatarUrl: "https://storage.example/avatars/uid_sara/profile.jpg", pairId: PAIR }
    );

    const pair = await readPair();
    expect(pair).toMatchObject({
      userBAvatarUrl: "https://storage.example/avatars/uid_sara/profile.jpg",
    });
    // Only Sara's side moved — Ali's fields are untouched by her edit.
    expect(pair).not.toHaveProperty("userAAvatarUrl");
  });

  it("does nothing when neither field actually changed", async () => {
    await fire(
      ALI,
      { name: "Ali", avatarUrl: null, pairId: PAIR, ratingCount: 3 },
      { name: "Ali", avatarUrl: null, pairId: PAIR, ratingCount: 4 }
    );

    const pair = await readPair();
    expect(pair).not.toHaveProperty("userAName");
  });

  it("does nothing for a user with no pair yet", async () => {
    await db().doc(userPath("uid_solo")).set({
      uid: "uid_solo",
      name: "Solo",
      avatarUrl: null,
      pairId: null,
    });

    await fire(
      "uid_solo",
      { name: "Solo", avatarUrl: null, pairId: null },
      { name: "Solo Renamed", avatarUrl: null, pairId: null }
    );

    // No pair document exists for this uid, so there is nothing to have
    // updated — the assertion is just that firing this did not throw.
    await expect(readPair()).resolves.toBeDefined();
  });
});
