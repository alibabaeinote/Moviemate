# MovieMate web

A real (not a demo) web client against `moviemate-prod-2026`: Google
sign-in, profile, onboarding (genre pick + Taste Dial rating deck), and
pairing (invite/join) so far — built incrementally, same backend and
schema as the Android app.

The route through these (which screen to land on) is decided **once**
per sign-in, mirroring `AppEntryViewModel.startRouteFor` in the Android
app — not continuously recomputed from live data. Recomputing on every
Firestore snapshot would yank you out of, say, the invite-code screen the
instant your own `pairId` loads, before you've had a chance to copy it.
Each screen instead navigates forward explicitly once it's actually done
(reaching the rating target, successfully pairing), the same way Android's
screens do.

## Run it locally first

Firebase Auth's popup sign-in only works from an **authorized domain**.
`localhost` is authorized by default for every Firebase project, so you
don't need to deploy anything to try this:

```
cd web
python3 -m http.server 5000
```

Open `http://localhost:5000`. (Any static server works — `npx serve` is
fine too. Opening `index.html` directly via `file://` will NOT work;
Firebase Auth's popup flow requires a real http(s) origin.)

## Before sign-in will actually work

Two things still need to be deployed from the repo root, with the Firebase
CLI logged into the account that owns `moviemate-prod-2026`:

```
npm install -g firebase-tools   # once, if you don't have it
firebase login
firebase deploy --only firestore:rules --project moviemate-prod-2026
```

(`--project` is spelled out every time below because `.firebaserc` is
gitignored in this repo — nothing here depends on a local alias file
existing.)

Without that second command, Firestore is still on the default
"deny everything" rules the console starts you with — sign-in will
succeed, but writing the profile document will fail with
`permission-denied`. The rules being deployed are the same ones already
tested against the emulator (`rules-tests/`) — nothing new to write.

## Deploying it for real (so a link works for anyone, not just localhost)

```
firebase deploy --only hosting --project moviemate-prod-2026
```

This publishes `web/` to `https://moviemate-prod-2026.web.app`, which
Firebase auto-authorizes for sign-in — no extra domain configuration
needed.

## Before pairing will work: Cloud Functions have to be live

`createPair`/`joinPair` (and everything past them — onboarding films,
daily match, watchlist search) are **Cloud Functions**, not direct
Firestore writes — the security rules deliberately close client writes to
`/pairs` and leave that to the server (see `firestore.rules`). None of
that has been deployed yet. Three things, in order:

1. **Upgrade the Firebase project to the Blaze (pay-as-you-go) plan.**
   This repo's functions use the 2nd-gen `firebase-functions/v2` API,
   which Cloud Functions requires Blaze to deploy at all — not just for
   TMDB's outbound network calls. Blaze's free tier (2M invocations/month)
   comfortably covers a two-person app; this needs a payment method on
   file regardless. Console → Project settings → Usage and billing →
   Modify plan.
2. **Get a TMDB API read access token** (themoviedb.org → an account →
   Settings → API → request a key → copy the "API Read Access Token", not
   the shorter v3 key) and set it as a secret:
   ```
   npx firebase-tools functions:secrets:set TMDB_ACCESS_TOKEN --project moviemate-prod-2026
   ```
   (pastes in, hidden, when prompted).
3. **Deploy the functions:**
   ```
   npx firebase-tools deploy --only functions --project moviemate-prod-2026
   ```

Until all three are done, "Get an invite code" / "I have a code" will
fail with a `functions/not-found`-style error — surfaced in the UI, not a
silent hang.

## What this does so far

- Signs in with Google (`signInWithPopup`), seeding `users/{uid}` on the
  account's **first** sign-in only — mirrors
  `FirebaseAuthRepository.signInWithGoogle` in the Android app field for
  field, so both clients write the same document shape.
- Lets you edit and save your display name, via the same
  `name`/`avatarUrl`-only write the security rules allow a user to make on
  their own document.
- Onboarding: pick genres, then rate a deck of real TMDB films with a
  Taste Dial (0-100). Unpaired, scores buffer in `localStorage` (mirrors
  `OnboardingDraftStore.kt`); once paired, they write straight to
  `pairs/{pairId}/ratings`. Either way, reaching the Kotlin app's actual
  rating target (10 rated is what the Android app requires) flushes the
  buffer and hands off to pairing.
- Pairing: get an invite code or enter one you were given. There's no
  live "partner joined" indicator here on purpose (see the routing note
  above) — once you're paired, tap Continue.

## What's still not built

- Avatar upload (Storage) — the Google account photo is used as-is.
- The daily Match screen and watchlist/search. These are real next
  slices, not skipped by accident — the Android app remains the only
  client that has them built. Reaching "paired + onboarding done" here
  currently lands on a placeholder card that says so.
