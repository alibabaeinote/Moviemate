# MovieMate

One film a night, chosen for two people.

MovieMate is an Android app for a couple or a pair of close friends who already
know each other. Every day it suggests **one** film at the intersection of both
their tastes, and both of them have to say "We're in" before anything happens.
No matching with strangers, no feed, no watch party — just the "what should we
watch?" decision, made once and quickly.

The product decisions behind all of this live in [`docs/`](docs/). That folder
is the source of truth; this README says what has actually been built.

---

## Where the project stands

| Layer | State |
|---|---|
| Firestore schema + security rules | Written, **not yet deployed or emulator-tested** |
| Recommendation engine | Implemented, 42 unit tests passing |
| TMDB client + 6-month cache | Implemented, not yet run against the live API |
| Cloud Functions (11) | Implemented, TypeScript compiles clean, **not deployed** |
| Notification layer (7 types) | Implemented, caps unit-tested, **no real device test** |
| Android design system | Tokens, type scale and core components written |
| Android auth screens | Sign up / sign in / forgot password wired to Firebase Auth |
| Android app screens | **Routed but not built** — placeholders behind the nav graph |
| Android build | **Never compiled** — see "Honest limits" below |

### Honest limits

Two things in here have not been executed, only written:

1. **No Android code has been compiled.** The environment this was written in
   has no Android SDK, so `android/` has never been through the Kotlin
   compiler. Expect to fix import and API-signature errors on the first
   `./gradlew assembleDebug`. The Cloud Functions, by contrast, do build and
   their tests do run — treat those as verified and the Android side as a
   careful draft.
2. **Nothing is deployed.** No Firebase project, no TMDB key, no rules
   deployment, no emulator run. Phase 0 below is still entirely ahead of you.

---

## Layout

```
firestore.rules          Security rules (hardened — see Deviations)
firestore.indexes.json   Composite indexes the queries need
firebase.json            Rules, functions and emulator config

functions/               Cloud Functions (TypeScript, Node 22)
  src/config/            Every tunable algorithm weight, in one file
  src/domain/            Taste profiles, scoring, reason text — pure and tested
  src/tmdb/              TMDB client and the Firestore cache layer
  src/notifications/     Send path, frequency caps, copy
  src/triggers/          Firestore triggers
  src/scheduled/         Cron functions
  src/callable/          Client-callable functions
  test/                  Unit tests (vitest)

android/                 Kotlin + Jetpack Compose app (min SDK 26)
  app/src/main/java/com/moviemate/app/
    ui/theme/            Locked design tokens — colours, type, spacing
    ui/components/       CTA, pill tags, bottom nav, Taste Dial
    ui/screens/          Auth screens; everything else is a placeholder
    data/                Firestore models and repositories
    nav/                 Routes and notification deep links

docs/                    PRD, schema, algorithm, design system, checklists
docs/prototypes/         The v8 HTML prototypes — visual reference, not code
```

---

## Getting it running

### Phase 0 — the human-only prerequisites

None of this can be scripted; it needs console access.

1. Create a Firebase project (the docs assume `moviemate-prod`).
2. Enable **Firestore** in production mode.
3. Enable **Authentication → Email/Password**.
4. Enable **Cloud Messaging**.
5. Download `google-services.json` into `android/app/`. It is git-ignored, and
   should stay that way.
6. Get a [TMDB API key](https://www.themoviedb.org/settings/api).

### Backend

```bash
cd functions
npm install
npm test          # 42 unit tests, no emulator needed
npm run build

cp .env.example .env                      # local emulator only
firebase functions:secrets:set TMDB_ACCESS_TOKEN   # real deploys

cp ../.firebaserc.example ../.firebaserc  # point at your project

firebase deploy --only firestore:rules,firestore:indexes
firebase deploy --only functions
```

To run against the emulator suite instead: `npm run serve`.

### Android

```bash
cd android
cp local.properties.example local.properties   # add your TMDB token
```

Then add the font files — `app/src/main/res/font/README.md` lists exactly which
files and where to get them — and open the folder in Android Studio.

The functions deploy to `europe-west1`; `PairRepository` and `firebase.json`
both name that region, so change both together if you move it.

---

## How the recommendation works

Two people rate different films during onboarding, so there is almost no overlap
to compare directly. That makes this content-based filtering, not collaborative
filtering: build a taste profile per person, then predict how each of them would
score a film neither has seen.

```
predictedScore(user, film) = 0.60 × mean(genreAffinity of the film's genres)
                           + 0.25 × eraAffinity[film's decade]
                           + 0.15 × mean(countryAffinity of the film's countries)

tasteScore  = mean(predictedA, predictedB) − 0.40 × |predictedA − predictedB|
finalScore  = 0.85 × tasteScore + 0.15 × (tmdbRating × 10)
```

The divergence penalty is the part that matters. A film predicted 95 for one
person and 40 for the other averages to a comfortable 67.5 — but it is not a
shared pick, it is one person's film with the other along for the ride.
Penalising the gap is what makes the output a *joint* recommendation.

If nothing clears **40**, the app says so rather than offering a weak film.
Showing a bad pick costs more trust than showing none.

Every number above is an untested assumption from the design docs. They all live
in [`functions/src/config/algorithm.ts`](functions/src/config/algorithm.ts) so
they can be tuned against real "We're in" rates without touching scoring code.

---

## Deviations from the supplied documents

Each of these is a place where the code does something the docs did not say, or
said differently. Worth a review before they harden into precedent.

### 1. A real hole in the security rules (fixed)

The draft `firestore.rules` guarded mutual commitment like this:

```
onlyChangedField(resource.data, request.resource.data, "commitStatus.userA")
```

`affectedKeys()` reports only **top-level** keys, so editing `commitStatus.userA`
and editing `commitStatus.userB` both look like one change to `commitStatus`.
The check could not tell them apart, and either partner could have set both
flags — exactly the one-sided decision PRD §7.2 exists to prevent.

It is now replaced by `commitsOnlyForSelf()`, which pins the *other* person's
flag to its stored value.

### 2. The quality-bonus formula contradicts itself

Algorithm doc §5 writes `qualityBonus = (tmdbRating / 10) × 10`, which evaluates
to `tmdbRating` — a 0-10 number. Its own inline comment says "rescale to 0-100",
and the §6 summary writes `tmdbRating × 10`, which is 0-100.

Taken literally, §5 would mix a 0-10 term into a 0-100 blend and quietly drop
about 90% of the intended quality weight. **The code follows the stated intent
and §6 (0-100).** This needs a product decision to confirm.

### 3. Client writes to `/pairs` are closed

The draft let any pair member update the pair document, which would have
included `aBothOnboarded` and `streakCount` — fields the schema doc says must be
server-owned. Joining now goes through the `joinPair` callable, so the invite
code, its 7-day expiry and the "seat still free" check all happen in one
transaction.

### 4. Fields added that the docs do not mention

- `users.timezone` and `pairs.timezone` — the daily match is specified as "9am
  local to each pair", which is not computable without one. The scheduler runs
  hourly and picks out pairs for whom it is currently 09:00.
- `users.lastActiveAt` — needed for the documented "don't notify someone who is
  already in the app" rule.
- `matches.shortlist` — stores the ranked top 3 so rejection can advance without
  re-scoring, and so the 3-up fallback screen has something to render.
- `matches.scheduledFor` / `reminderSent` — the docs describe a suggested time
  and a 15-minute reminder but never say where the time is stored.
- `notificationLog` — frequency caps need a record of what was sent when.

### 5. Ratings use a deterministic document id

`pairs/{pairId}/ratings/{userId}_{filmId}`. Re-rating a film becomes an update
rather than a second row, which handles the documented "Duplicate Rating" case
without extra logic.

---

## Open questions still unanswered

Carried over from PRD §12, plus what this implementation surfaced:

- **Offline rating** — still undefined. Ratings currently require connectivity.
- **The quality-bonus contradiction** above needs a decision.
- **Candidate pool quality** — the pool comes from TMDB `discover` sorted by
  popularity, filtered to ≥200 votes. That biases toward recent mainstream
  films, which may fight against the taste profile. Worth watching once there is
  real data.
- **Cold start** — with ~10 ratings, profiles are noisy and predictions
  unreliable. A known limit of content-based filtering on thin data.
- **Custom icon set** — the design system calls for a bespoke 24×24 stroke set.
  The nav currently uses Material icons as placeholders.

---

## Non-negotiables

Things that will quietly break the product if changed without thought:

1. **Ratings are a continuous 0-100 Taste Dial.** Never a 1-5 integer. Every
   taste profile in the system reads this as a float.
2. **Both users must commit independently** before anything advances.
3. **One suggestion at a time.** Three-up only appears after three rejections —
   showing a menu up front re-creates the disagreement the app exists to remove.
4. **"Watched" is manual.** No calendar or streaming integration. This is a
   confirmed product decision, not a temporary shortcut.
5. **The TMDB cache expires after 6 months.** That is a Terms of Use obligation,
   not a tuning parameter.
6. **TMDB attribution stays visible** on the About screen for as long as the app
   shows TMDB data.

---

## Testing

```bash
cd functions && npm test
```

42 tests, covering the scoring engine (including the divergence penalty and the
no-match threshold), reason-text generation, the notification frequency caps,
and per-pair local-time scheduling across timezone boundaries.

There are no Android tests yet, and no emulator tests of the security rules —
the rules changes above are the first thing that deserve them.
