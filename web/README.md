# MovieMate web — sign-in + profile

A minimal, real (not a demo) web client: Google sign-in via Firebase Auth,
and editing the same `users/{uid}` document the Android app reads and
writes. Scoped to exactly that — no Match/Watchlist/Us screens here yet.

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

## What this does and doesn't do

- Signs in with Google (`signInWithPopup`), seeding `users/{uid}` on the
  account's **first** sign-in only — mirrors
  `FirebaseAuthRepository.signInWithGoogle` in the Android app field for
  field, so both clients write the same document shape.
- Lets you edit and save your display name, via the same
  `name`/`avatarUrl`-only write the security rules allow a user to make on
  their own document.
- Does **not** upload a custom avatar photo yet (Storage upload) — the
  Google account photo is used as-is. A real next step, not done here to
  keep this first slice small.
- Does **not** touch pairing, matches, watchlist, or anything else — the
  Android app remains the only client that has any of that built.
