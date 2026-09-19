// TMDB v3 API key — free, no billing plan required, unlike Cloud Functions.
// Get one: themoviedb.org → create an account → Settings → API → request an
// API key ("Developer" is fine for a personal project) → copy the "API Key
// (v3 auth)" value specifically — NOT the longer "API Read Access Token"
// underneath it. That second one is a Bearer token meant for server-side use;
// this app calls TMDB straight from the browser (no Cloud Functions instance
// to hide it behind on the Spark plan), and TMDB's v3 api_key is designed
// for exactly that — it shows up in every request URL by design, so there's
// nothing to gain by keeping it out of this file.
export const TMDB_API_KEY = "PASTE_YOUR_TMDB_V3_API_KEY_HERE";
