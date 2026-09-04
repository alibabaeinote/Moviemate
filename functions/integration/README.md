# Cloud Functions integration tests

Runs the real trigger and callable handlers against the Firestore emulator.

```bash
cd functions
npm install
npm run test:integration
```

Needs Java. No network: every film these tests touch is seeded into
`filmCache`, and users are seeded with no FCM tokens so notifications
short-circuit before reaching messaging.

## Why these exist separately from `npm test`

The unit suite covers pure logic — scoring, streak arithmetic, mutual score,
frequency caps. It cannot cover what these handlers actually do, which is write
to **several documents at once** and, in three cases, write back to the document
that triggered them.

That last part is the real subject here. `onMatchUpdate` handles commitment,
mutual confirmation and "watched" on the same match document, so each transition
re-fires the trigger. The guards are on the before→after edge rather than the
after-state alone, and a wrong guard is an infinite loop found in production on
a Firestore bill. Every one of those guards has a test that fires the handler a
second time with its own write and asserts nothing moves.

## Coverage

| File | Proves |
|---|---|
| `matchLifecycle.test.ts` | Mutual commitment stamps `bothConfirmedAt` and promotes the film to the watchlist; "watched" marks the match, mirrors onto the watchlist and advances the streak; the streak respects same-day, grace window and lapse; **no transition re-runs on its own write** |
| `rating.test.ts` | Onboarding counts and completion, `aBothOnboarded` flipping only when both are done, and mutual score staying null until the second rating lands |
| `callables.test.ts` | Sequential rejection walking the shortlist and unlocking the 3-up fallback only at the end; invite codes issued, claimed once, and rejected when expired, reused, or your own |
| `watchlist.test.ts` | "I'm in too" promotes to ready, one-sided does not, and a watched film never goes back |

## Not covered

`generateDailyMatch` and `sendWatchReminders` are not here. Both call TMDB, and
faking that needs the candidate pool injected rather than fetched — worth doing,
but it is a refactor of the function, not a test.
