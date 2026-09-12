import { afterAll, beforeEach, describe, expect, it } from "vitest";
import { Timestamp } from "firebase-admin/firestore";
import { ALI, PAIR, SARA, clearAll, db, fft, pairPath, seed } from "./helpers";
import { generateMatchForPair, type CandidatePoolProvider } from "../src/domain/matchService";
import type { PairDoc, ScorableFilm } from "../src/types";

/**
 * The scoring half of the daily match, run for real against the emulator.
 *
 * generateMatchForPair's own candidate pool (buildCandidatePool) reaches TMDB,
 * which this suite must never do — so every test here injects a fixed pool
 * instead of the real one. That is the whole reason buildPool is a parameter:
 * see the doc comment on generateMatchForPair.
 */

afterAll(() => {
  fft.cleanup();
});

async function seedFilmCache(filmId: string, over: Partial<ReturnType<typeof filmCacheDoc>>) {
  await db().doc(`filmCache/${filmId}`).set({ ...filmCacheDoc(filmId), ...over });
}

function filmCacheDoc(filmId: string) {
  return {
    tmdbId: filmId,
    title: `Film ${filmId}`,
    posterPath: null,
    genres: ["Drama"],
    releaseYear: 2020,
    runtime: 100,
    overview: "",
    tmdbRating: 5,
    countries: ["US"],
    cachedAt: Timestamp.now(),
    expiresAt: Timestamp.fromMillis(Date.now() + 180 * 24 * 60 * 60 * 1000),
  };
}

async function rate(uid: string, filmId: string, score: number) {
  await db().doc(`pairs/${PAIR}/ratings/${uid}_${filmId}`).set({
    userId: uid,
    filmId,
    score,
    isInitialOnboarding: true,
    reactionEmoji: null,
    ratedAt: Timestamp.now(),
  });
}

async function loadPair(): Promise<PairDoc> {
  const snapshot = await db().doc(pairPath()).get();
  return snapshot.data() as PairDoc;
}

async function matchDocs() {
  const snapshot = await db().collection(`pairs/${PAIR}/matches`).get();
  return snapshot.docs.map((d) => d.data());
}

const poolOf = (films: ScorableFilm[]): CandidatePoolProvider => async () => films;

beforeEach(async () => {
  await clearAll();
  await seed();
});

describe("generateMatchForPair", () => {
  it("suggests the injected candidate when it clears the threshold", async () => {
    // Both partners love 2020s American sci-fi — genre, era and country all
    // point the same way, so predictedA and predictedB land close together.
    await seedFilmCache("rated_scifi", {
      genres: ["Science Fiction"],
      releaseYear: 2021,
      countries: ["US"],
    });
    await rate(ALI, "rated_scifi", 95);
    await rate(SARA, "rated_scifi", 95);

    const candidate: ScorableFilm = {
      filmId: "cand_scifi",
      genres: ["Science Fiction"],
      releaseYear: 2022,
      countries: ["US"],
      tmdbRating: 8,
    };

    const result = await generateMatchForPair(PAIR, await loadPair(), poolOf([candidate]));

    expect(result?.noMatches).toBe(false);
    expect(result?.filmId).toBe("cand_scifi");
    expect(result?.score).toBeGreaterThan(80);

    const [match] = await matchDocs();
    expect(match).toMatchObject({ filmId: "cand_scifi", status: "suggested", attemptNumber: 1 });
    expect(match?.shortlist).toHaveLength(1);
  });

  it("falls back to the No Matches scenario when nothing clears the threshold", async () => {
    // Both partners have already told the app they dislike exactly this kind
    // of film — low genre, era AND country affinity, plus a poor TMDB score.
    await seedFilmCache("rated_horror", {
      genres: ["Horror"],
      releaseYear: 1990,
      countries: ["FR"],
    });
    await rate(ALI, "rated_horror", 5);
    await rate(SARA, "rated_horror", 5);

    const candidate: ScorableFilm = {
      filmId: "cand_horror",
      genres: ["Horror"],
      releaseYear: 1965,
      countries: ["FR"],
      tmdbRating: 1,
    };

    const result = await generateMatchForPair(PAIR, await loadPair(), poolOf([candidate]));

    expect(result?.noMatches).toBe(true);
    expect(result?.filmId).toBe("");

    const [match] = await matchDocs();
    expect(match?.status).toBe("dismissed");
    expect(match?.noMatchesReason).toBeTruthy();
  });

  it("does nothing when the pair has no second member yet", async () => {
    const pair = await loadPair();

    // No pool argument reaches this path — the userB guard returns first, so
    // the default (real, TMDB-backed) buildCandidatePool is never invoked.
    const result = await generateMatchForPair(PAIR, { ...pair, userB: null });

    expect(result).toBeNull();
    expect(await matchDocs()).toHaveLength(0);
  });
});
