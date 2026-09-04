# Security rules tests

Runs the real `../firestore.rules` against the Firestore emulator. The rules
file is read directly, not copied, so these tests cannot drift from what
deploys.

```bash
cd rules-tests
npm install
npm test          # starts the emulator, runs the suite, shuts it down
```

Needs Java (the emulator is a JVM process) and downloads the emulator jar on
first run.

## What is covered

| File | Proves |
|---|---|
| `commit.test.ts` | **Neither partner can commit on the other's behalf** — the guarantee PRD §7.2 rests on, and the one the v1 rules failed to provide. Also that `bothConfirmedAt`, `score` and `attemptNumber` stay server-owned, and that "watched" is one-way and cannot be back-dated |
| `access.test.ts` | Pair membership gates every read; `/pairs` takes no client writes; `/users` updates are field-whitelisted; matches are server-created; `filmCache` is read-only; `notificationLog` is invisible |
| `data.test.ts` | The Taste Dial 0-100 range is enforced in the rules, ratings belong to their author, and the watchlist's derived fields cannot be forged |

## A note on the emulator log

Denied updates to a match log `evaluation error at <rule>` next to a correct
`false`. Rules deny on an evaluation error, and every allow and deny here
behaves as intended — but the cause is not yet understood, and it means a real
rule error would hide in the noise. See the note in `firestore.rules` above
`confirmsWatchedOnly`.
