---
name: post-feature-review
description: Rigorous, evidence-based review to run immediately after implementing any feature in this repo (Android, Cloud Functions, or the no-Blaze web client), before considering it done. Use whenever a feature, screen, rule change, or migration has just been built and pushed/committed, or the user asks to "check what was just built," review a feature, or run the post-feature checklist.
---

# Post-Feature Code Review

Run this after finishing any feature-sized change in this repo — not after every
small edit, but whenever a self-contained unit of work (a screen, a rules
change, a new client-side module, a migration) has just been completed. Review
before modifying: report findings first; only fix things afterward if the user
asks you to act on them.

## Purpose

Determine whether the feature just built is:

- Functionally correct
- Consistent with the stated requirement (including the scope the user actually
  asked for, not more or less)
- Safe (Firestore rules are the real authority in this repo — never assume
  client-side checks are enough)
- Architecturally consistent with how the rest of the repo already does things
- Reliable under edge cases, races, and partial failures
- Maintainable
- Properly tested — and consistent with this repo's own testing conventions
  (see "Repo-specific context" below)
- Free of unnecessary complexity, dead code, or scope creep

## Review method

1. Identify what was actually built: `git diff --stat` against the commit(s)
   for this feature, or the working tree diff if not yet committed.
2. Re-state the requirement in one or two sentences, from the actual user ask
   in this conversation (not from assumption).
3. Read every changed file in full, and enough surrounding/original code to
   understand what it's plugging into.
4. Find the closest existing precedent for the same kind of change elsewhere
   in the repo (a similar rule, a similar client module, a similar Android
   ViewModel) and compare against it — inconsistency with an established
   pattern is itself a finding.
5. Run the checks that exist (see below) — actually run them, don't assume.
6. Trace the main execution path by hand, end to end.
7. Actively try to break it: races, partial writes, repeated actions, empty/
   missing data, permission boundaries, stale listeners.
8. Check regression surfaces: what else reads/writes the same documents,
   collections, or shared modules this feature touches.
9. Report findings using the format below. Do not fix anything during this
   pass unless explicitly asked to.

## Repo-specific context (check this first, it changes what "properly tested" means)

- **Firestore rules changes** → there is a real, fast test suite:
  `cd rules-tests && npm test` runs against the local emulator. Any rules
  change without a corresponding test in `rules-tests/*.test.ts` is a gap,
  not just a nice-to-have — this repo's own convention (see `pairing.test.ts`,
  `match.test.ts`) is to test every new `allow create/update` branch, positive
  and negative, including stranger/cross-pair access.
- **Cloud Functions changes** (`functions/src/**`) → `functions/test/*.test.ts`
  (unit, pure-logic) and `functions/integration/*.test.ts` (emulator) are the
  precedent. Pure domain logic (`functions/src/domain/*.ts`) is always unit
  tested there — if a web-client port of the same logic (`web/match-engine.js`
  is the existing example) has no equivalent test, that's a real gap to flag,
  not a style preference.
- **`web/*.js` changes** → there is currently **no test runner configured for
  `web/`** (no package.json, no vitest/jest config). This is itself worth
  re-flagging on every review until it's fixed, not silently accepted each
  time — pure-logic files like `match-engine.js` should have unit tests just
  like their `functions/src/domain/` counterparts. Runnable-today checks for
  `web/`: `node --check <file>.js` for syntax, and manual trace for logic
  (there's nothing else until a runner exists).
- **Android changes** → check `android/app/src/test/**` for the relevant
  ViewModel/screen; this repo's convention is one test file per ViewModel
  (`*ViewModelTest.kt`) using fakes (`Fake*Store.kt`), not mocks. Missing
  tests here are a gap, matching the same bar the Kotlin code has held
  throughout this project.
- **No-Blaze fallbacks are a recurring pattern** (see `firestore.rules`'
  deviations b/d/e and `web/{app.js,match.js}`): a client performs a write a
  Cloud Function would otherwise make, gated by a narrow security rule rather
  than server-side verification. This is a deliberately accepted trust level
  for a two-person app — don't flag "the client computes this instead of the
  server" as a finding on its own. DO flag it if: the corresponding rule is
  missing, the rule is looser than the specific write it's meant to gate
  needs, or the deviation isn't documented in `firestore.rules`' top comment
  and `web/README.md`'s deviation list the way the existing ones are.
- **Cross-client consistency**: this Firestore project is shared by the
  Android app and the web client. A schema or behavior change on one side
  (e.g., a field the web client leaves unset, or a status value it never
  transitions to) should be checked against how the *other* client reads that
  same field — see `MatchPhase.kt`'s read-order as the existing example of
  what to check against.

## Checklist

Work through every section below. Skip a section only when it's genuinely not
applicable (e.g. no UI changed → skip Accessibility), and say so explicitly
rather than silently omitting it.

### 1. Scope & requirement verification
- [ ] List every file/module/rule/config changed.
- [ ] Confirm the implementation matches what was actually asked for in this
      conversation — quote or paraphrase the actual request.
- [ ] Note anything missed, partially done, or reinterpreted.
- [ ] Note any undocumented behavior the change introduces.
- [ ] Confirm unrelated behavior wasn't unintentionally changed — if it was
      (even for a good reason), call it out explicitly as a disclosed scope
      expansion, not a silent side effect.
- [ ] Flag scope beyond what was requested.

### 2. Functional correctness
- [ ] Primary flow works as intended (trace it by hand).
- [ ] All conditional branches inspected, not just the happy one.
- [ ] State transitions and synchronization (especially anywhere using
      `onSnapshot`, ViewModel `StateFlow`, or the web client's module-level
      mutable state objects like `ob`/`mt`) are correct under out-of-order or
      overlapping updates.
- [ ] Loading/success/empty/error/disabled/retry states, where applicable.
- [ ] Async code checked for races and stale-closure bugs.
- [ ] Repeated actions and idempotency where a user or listener could
      plausibly fire the same write twice.
- [ ] Null/undefined handling, off-by-ones, type coercion.

### 3. Edge cases & failure modes
Actively try to break it — missing/empty/null data, invalid or oversized
input, duplicate/rapid actions, slow or dropped network, timeouts, partial or
unexpected responses, expired/unauthenticated session, concurrent writes from
both partners, refresh/reload mid-operation, navigating away and back,
partial failure in a multi-step write (e.g. the batch writes this repo uses
for pairing/match generation). Call out anything that could leave Firestore
in an inconsistent state.

### 4. Regression risk
- [ ] Existing flows/screens that read or write the same documents.
- [ ] Shared modules touched (`match-engine.js`, `tmdb.js`, `AppGraph.kt`,
      shared Compose components, etc.).
- [ ] Firestore rules: run `cd rules-tests && npm test` and confirm the
      **existing** tests still pass, not just the new ones.
- [ ] Routing/navigation (web's `decideInitialSection`/`maybeDecideInitialRoute`,
      Android's `AppEntryViewModel.startRouteFor`).
- [ ] Auth/permission boundaries.
- [ ] Config/environment changes (`firebase.json`, `.gitignore`, secrets).
- [ ] Dependency changes (should be rare and justified in this repo).

### 5. Architecture & separation of concerns
- [ ] Logic lives in the right layer (domain logic vs. UI vs. Firestore
      access) — compare against the closest existing precedent.
- [ ] No new abstraction without a demonstrated need across ≥2 call sites.
- [ ] No architectural shortcut likely to become debt (e.g. business logic
      embedded directly in a click handler where the rest of the codebase
      would factor it out).

### 6. Code quality & maintainability
- [ ] Naming communicates intent.
- [ ] No dead code, no leftover debug logging, no commented-out code.
- [ ] Comments explain non-obvious *why*, not *what* (matches this repo's own
      established comment style — see any existing file for the bar).
- [ ] Error handling is explicit, not swallowed silently without a reason
      stated in a comment (the codebase does this deliberately in a few
      places — e.g. `ensurePromotedToWatchlist`'s idempotent-create pattern —
      always with a comment explaining why the failure is expected).

### 7. Security & privacy
- [ ] Every new client-writable path has a corresponding Firestore rule that
      is the actual authority — never assume UI logic is enough.
- [ ] No secret committed (check `.gitignore` covers any new config file that
      should never reach the public repo, same as `web/tmdb-config.js`).
- [ ] Authorization boundaries: can a stranger, or the *other* pair member in
      the wrong role, do something they shouldn't? (This is exactly what
      `rules-tests/*.test.ts` should demonstrate — check it does.)
- [ ] No sensitive data logged or exposed in error messages shown to users.

### 8. Data integrity
- [ ] Firestore writes validated by rules, not just client code.
- [ ] Schema compatibility with existing documents (Android and web both read
      the same collections — see "Cross-client consistency" above).
- [ ] Batch/transaction boundaries: what happens if a multi-step write
      partially fails (e.g. the match doc is created but the
      `lastMatchGeneratedAt` update in the same batch doesn't land)?
- [ ] Concurrency: two writers touching the same document (both partners
      committing, both requesting a match, etc.) — trace what the security
      rules actually allow, not just what the UI intends.

### 9. API & integration review
- [ ] External API calls (TMDB, Firebase Auth) handle non-2xx responses,
      malformed JSON, and rate limiting without crashing the flow.
- [ ] No breaking change to a Firestore document shape another client reads.

### 10. Performance & resource usage
- [ ] No unbounded loops or unnecessarily repeated network/DB calls on every
      re-render (check anything inside a `renderX()` function called from an
      `onSnapshot` callback — this is the most common place this repo's
      client code re-does expensive work needlessly).
- [ ] Listener cleanup: every `onSnapshot` subscription started by a screen
      has a corresponding unsubscribe (on sign-out, on navigating away, on
      re-subscribing to a different pair/doc).
- [ ] No obviously excessive TMDB request volume (this repo has already hit
      this once — see `candidatePoolSize`'s comment in `match-engine.js` — so
      it's a known-real class of issue here, not theoretical).

### 11. UI/UX implementation integrity
- [ ] Matches the intended states from the actual design reference for this
      feature (Android's screen/ViewModel, or the FigJam/PRD if this is a new
      web slice with no Android precedent yet).
- [ ] Repeated submission prevented where a double-click would cause a
      duplicate write (buttons should disable on click, matching the existing
      pattern throughout `app.js`).
- [ ] Uses existing design tokens/CSS classes rather than introducing new
      ones without reason (web) or existing Compose theme (Android).

### 12. Accessibility
Skip with a note if no user-facing UI changed. Otherwise: semantic structure,
keyboard reachability, labels on interactive elements, color-independent
state communication.

### 13. Tests
- [ ] Run whatever test suite actually exists for the layer touched (see
      "Repo-specific context" above for which one).
- [ ] Confirm existing tests still pass — not just new ones.
- [ ] Identify important behavior with **no** coverage, especially pure logic
      ported between platforms (server ↔ web) — this repo's existing
      convention is to unit-test that logic, so its absence is a gap to name
      explicitly, not something to wave through because "it's just a port."
- [ ] Don't recommend tests purely for coverage percentage — prioritize ones
      that would catch a realistic regression.

### 14. Observability & debuggability
- [ ] Failures surface to the user via the existing `showStatus`/error-message
      pattern (web) or UI-state error handling (Android) — not a silent
      console-only failure.

### 15. Dependencies & configuration
- [ ] Any new dependency is justified; this repo adds dependencies rarely —
      treat a new one as a real design decision worth explaining.
- [ ] Config/secret handling follows the existing gitignore + `.example.js`
      pattern (see `web/tmdb-config.example.js`) if a new secret-shaped file
      is introduced.

### 16. Complexity & technical debt
- [ ] No unnecessary abstraction, wrapper, or defensive code for a failure
      mode that can't actually happen given this repo's constraints (e.g. a
      2-person trust model — don't recommend multi-tenant-grade hardening
      where the codebase has already made an explicit, documented decision
      not to need it).
- [ ] No TODO/FIXME left behind without an explanation of why it's deferred.

### 17. Repository hygiene
- [ ] No debug logs, temp files, or accidental generated files.
- [ ] No secrets committed — double-check anything that looks like a key or
      token even in a file that seems unrelated.
- [ ] `rules-tests` pass, relevant `functions/test`/`android` tests pass,
      `node --check` passes on any changed `web/*.js`.
- [ ] `web/README.md` (or the relevant doc) updated if behavior, setup steps,
      or a deviation from the server/Android version changed.

## Required output format

```
## Review Summary
Feature:
Review scope:
Files inspected:
Tests/checks executed:
Overall implementation status:

## Findings
(repeat per finding, most severe first; state "no meaningful issue found" for
a section instead of inventing one)

[Severity] Short descriptive title
Location: file:line
Issue:
Evidence / Trigger:
Impact:
Recommended fix:
Test recommendation:

## Verification
Build: PASS / FAIL / NOT RUN
Type check: PASS / FAIL / NOT RUN
Lint: PASS / FAIL / NOT RUN
Relevant tests: PASS / FAIL / NOT RUN
Security concerns found: YES / NO
Regression risks found: YES / NO

## Final Assessment
Blocking issues:
Non-blocking issues:
Suggested tests:
Regression surfaces:
Review confidence: High / Medium / Low (explain what limits it — e.g. no live
browser/backend available to exercise the happy path end to end)
```

Severity scale: **P0** critical (security compromise, data loss, feature
unusable) · **P1** high (likely bug, broken important flow, real
auth/security problem) · **P2** medium (real but limited-impact correctness/
reliability/maintainability issue) · **P3** low (worth fixing, unlikely to
matter in normal operation) · **Note** non-blocking observation.

## Constraints

- Review before modifying. Do not fix findings during this pass unless asked.
- Do not rewrite working code for style preference.
- Do not introduce new architecture without demonstrated need.
- Do not manufacture findings to look thorough — say "no meaningful issue
  found" when that's true.
- Never report a check as PASS unless it was actually run or directly
  verified this session.
- Distinguish verified defects from hypotheses/risks explicitly.
