// Copy this file to tmdb-config.js (gitignored — see .gitignore) and paste
// your real key into THAT file, never this one. This repo is public: a real
// key committed here would be visible to anyone browsing GitHub, key-scraping
// bots included, whether or not the site is ever deployed. That's a
// different exposure than "the browser can see it" — TMDB's v3 api_key is
// designed to show up in every request URL a signed-in app makes, but there's
// no reason to also hand it out to everyone who reads the source on GitHub.
//
// Get one: themoviedb.org → create an account → Settings → API → request an
// API key ("Developer" is fine for a personal project) → copy the "API Key
// (v3 auth)" value specifically — NOT the longer "API Read Access Token"
// underneath it (that one's a Bearer token meant for server-side use).
//
// `firebase deploy --only hosting` uploads whatever's on disk in web/ at
// deploy time, not what's committed to git, so a gitignored tmdb-config.js
// still deploys fine — it just never reaches GitHub.
export const TMDB_API_KEY = "PASTE_YOUR_TMDB_V3_API_KEY_HERE";
