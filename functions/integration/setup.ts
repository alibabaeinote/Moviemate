/**
 * Points the Admin SDK at the emulator BEFORE any function module loads.
 *
 * src/lib/firebase.ts calls initializeApp() at import time, so these have to be
 * set in a setup file rather than inside a test.
 */
process.env["GCLOUD_PROJECT"] = "moviemate-test";
process.env["FIRESTORE_EMULATOR_HOST"] = "127.0.0.1:8080";

// The TMDB client reads this. No test here reaches the network: every film the
// functions look up is seeded into filmCache first, and a cache miss returns
// null rather than throwing.
process.env["TMDB_ACCESS_TOKEN"] = "test-token-unused";
