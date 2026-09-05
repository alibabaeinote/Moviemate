/**
 * MovieMate Cloud Functions.
 *
 * Everything that has to be a single source of truth across two users lives
 * here rather than on the client: onboarding completion, mutual commitment,
 * the daily match, and every notification.
 *
 * See docs/MovieMate-Codex-Implementation-Brief.md for the phase map.
 */
import { setGlobalOptions } from "firebase-functions/v2";

setGlobalOptions({ region: "europe-west1", maxInstances: 10 });

// Pairing
export { createPair } from "./callable/createPair";
export { joinPair } from "./callable/joinPair";

// Onboarding content
export { listGenres, getOnboardingFilms } from "./callable/onboardingFilms";

// Watchlist
export { searchFilms } from "./callable/searchFilms";

// Daily match lifecycle
export { generateDailyMatch } from "./scheduled/generateDailyMatch";
export { rejectMatch } from "./callable/rejectMatch";
export { chooseFallbackFilm } from "./callable/chooseFallbackFilm";
export { scheduleWatch } from "./callable/scheduleWatch";

// Firestore triggers
export { onRatingComplete } from "./triggers/onRatingComplete";
export { onMatchUpdate } from "./triggers/onMatchUpdate";
export { onWatchlistUpdate } from "./triggers/onWatchlistUpdate";
export { onWatchlistAdd } from "./triggers/onWatchlistAdd";

// Scheduled maintenance
export { refreshTmdbCache } from "./scheduled/refreshTmdbCache";
export { expireInviteCodes } from "./scheduled/expireInviteCodes";
export { sendWatchReminders } from "./scheduled/sendWatchReminders";
