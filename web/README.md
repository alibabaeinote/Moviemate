# MovieMate web

A real (not a demo) web client against `moviemate-prod-2026`: Google
sign-in, profile, onboarding (genre pick + Taste Dial rating deck),
pairing (invite/join), and the daily Match loop (find tonight's movie,
commit, watch, rate, streak) so far — built incrementally, same backend
and schema as the Android app.

The route through these (which screen to land on) is decided **once**
per sign-in, mirroring `AppEntryViewModel.startRouteFor` in the Android
app — not continuously recomputed from live data. Recomputing on every
Firestore snapshot would yank you out of, say, the invite-code screen the
instant your own `pairId` loads, before you've had a chance to copy it.
Each screen instead navigates forward explicitly once it's actually done
(reaching the rating target, successfully pairing), the same way Android's
screens do.

## Run it locally first

First, `cp web/tmdb-config.example.js web/tmdb-config.js` — a gitignored
file `app.js` imports at the top; without it, nothing here loads at all.
(You can leave the placeholder key in it for now — sign-in and pairing
don't need it. See "Before pairing or onboarding films will work" below
for getting a real one.)

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

## Before pairing or onboarding films will work: a TMDB API key

`createPair`/`joinPair`/`listGenres`/`getOnboardingFilms` are Cloud
Functions in the Android app, but 2nd-gen Cloud Functions require the
**Blaze (pay-as-you-go)** plan to deploy at all — not just for their
outbound TMDB calls — and that needs a payment method on file. If you
don't have a card to put on Blaze, this web client doesn't need one: it
never calls those functions at all.

Instead:
- Onboarding calls TMDB directly from the browser (`web/tmdb.js`), using
  TMDB's v3 `api_key` query-param auth, which is designed for exactly
  this — client-side calls, no backend needed to hide it.
- Pairing (`createPairDirect`/`joinPairDirect` in `web/app.js`) writes
  straight to Firestore instead of calling `createPair`/`joinPair`,
  gated by two rules added specifically for this (`claimsOwnPair()` and
  `joinsOpenSeat()` in `firestore.rules`, deviation **d** in the comment
  at the top of that file) plus a new `/inviteCodes` lookup collection so
  a joiner can resolve a shared code before they're a pair member. Both
  paths are covered by `rules-tests/pairing.test.ts` on the emulator.

One thing you still need regardless of Blaze — TMDB itself requires a
free account and key:

1. Go to themoviedb.org → create an account → Settings → API → request a
   key (choose "Developer" for a personal project).
2. Copy the **"API Key (v3 auth)"** value — the short one, *not* the
   longer "API Read Access Token" underneath it (that one's a Bearer
   token meant for server-side use).
3. `cp web/tmdb-config.example.js web/tmdb-config.js`, then paste your
   key into the **new** `tmdb-config.js`, not the example file.

`web/tmdb-config.js` is gitignored on purpose: this repo is public, and a
real key committed to it would be visible to anyone browsing GitHub (key-
scraping bots included), regardless of whether the site is ever deployed
— a different exposure than the browser network tab, which is where
TMDB's v3 `api_key` scheme expects it to show up. `firebase deploy
--only hosting` uploads whatever's on disk at deploy time, not what's
committed, so the gitignored file still deploys fine.

Without a real key in that file, genre pick and the rating deck will
show a "TMDB rejected the API key" error — surfaced in the UI, not a
silent hang. Pairing and everything else that only touches Firestore
works without it.

If you'd rather run the real Cloud Functions path instead (e.g. once you
do have Blaze available, or you're testing the Android app against the
same project), the callables are still in the repo and still deployable
the normal way (`firebase deploy --only functions`, after
`functions:secrets:set TMDB_ACCESS_TOKEN`) — this web client just doesn't
depend on them.

## The daily Match loop is also no-Blaze — with real deviations

`generateDailyMatch` (a scheduled function) and `onMatchUpdate` (a
Firestore trigger) are what run the recommendation engine and the
watched/streak side-effects in the Android app's backend — both Cloud
Functions, so both unusable here. `web/match-engine.js` and `web/match.js`
port that logic to the browser (`firestore.rules` deviation **e**;
`rules-tests/match.test.ts` covers the new rule paths), but three things
are genuinely different from the server version, not just relocated:

1. **No country signal.** `/discover/movie` never returns
   `production_countries` (only `/movie/{id}` does), and fetching that for
   every candidate in the pool was too many extra requests for a client
   build to make eagerly. Its weight is folded into genre/era instead —
   see the comment at the top of `match-engine.js`.
2. **No scheduler, so it's a button.** There's no free equivalent of a
   Cloud Scheduler job on Spark, so "tonight's movie" is found by tapping
   **Find tonight's movie**, not delivered automatically at a local 9am.
   The 20-hour gate in `createsTodaysMatch()` (firestore.rules) is the
   closest a security rule can get to "once per day" without a clock of
   its own; a real per-timezone local day needs something that runs
   hourly to check it.
3. **Client-computed, not server-verified.** The score and reason a match
   is written with come from the browser running the same algorithm the
   server would, not from re-deriving them inside the security rule (rules
   can gate *who* and *what shape*, not *whether the math was right*).
   That's the same trust level `createPair`/`joinPair`'s fallback already
   accepts — reasonable for two people who aren't adversarial toward each
   other, not something to build a multi-tenant product on.

Also intentionally not built yet, mirroring the Android app's own
`MatchPhase.kt` states this skips: the 3-up reject/fallback sequence
(declining today's pick to try alternates) and the schedule-watch time
picker. A "no match today" or a rejected day currently just waits for the
20-hour gate and the **Find another match** retry button, not a same-day
do-over. Push notifications (`partner_committed`, `both_confirmed`,
`daily_match`, `partner_watched`) are Cloud Functions too and don't fire
on this path at all — everything here is pull (open the app, see where
today's match stands), not push.

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
- Match: once both of you have rated 10 films, tap **Find tonight's
  movie** to get a suggestion (or the honest "nothing scored high enough
  today" state). Either of you taps **I'm in**; once you both have, it's
  confirmed and the film is added to the shared watchlist automatically.
  After watching it together, **We watched it** advances the streak and
  opens a one-film Taste Dial to rate what you saw.

## What's still not built

- Avatar upload (Storage) — the Google account photo is used as-is.
- Watchlist and Us screens (browsing the shared list, manual TMDB search,
  journey/compatibility stats). The daily Match promotes a mutually-
  committed film onto the watchlist collection, but there's no screen
  here yet to browse or search it directly — Android remains the only
  client with that built.
- The Match screen's own next slices: the 3-up reject/fallback sequence
  and the schedule-watch time picker (see "The daily Match loop is also
  no-Blaze" above for why, and what's here instead).
