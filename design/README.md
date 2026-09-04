# Design tokens

`tokens.json` is the single source of truth for every visual value in MovieMate.
Kotlin mirrors it; the documentation describes it. When they disagree, this file
wins.

## Commands

```bash
node design/validate-tokens.mjs     # check every rule — run before committing
node design/gen-usage-table.mjs     # regenerate the "which colour goes where" table
```

## What the validator checks

| Rule | Why it exists |
|---|---|
| References resolve, no cycles | A typo in `{ref.palette.blue.600}` should fail loudly, not silently render black |
| Tier discipline | Nothing outside `ref.*` may hold a raw hex, and themes may not reference each other |
| **Contrast** | Every colour declaring `$on` clears WCAG AA. This found a real v8 defect on its first run |
| Theme parity | `sys.dark` and `sys.light` expose identical role names, so a component can never find a role missing in one theme |
| Usage documented | Every semantic colour must say where it is used |
| Foundation coverage | All 17 foundations have tokens |
| 4px grid | Spacing values stay on the base grid |

Exit code 1 on any failure, so it drops straight into CI.

## The three tiers

```
ref.*    primitives      raw values, neutral names        never read by a component
sys.*    semantic roles  what it's FOR, per theme         the only tier components read
comp.*   component        one component's own knob        rare
```

The rule that matters: **a component needing a colour with no role in `sys.*` is
missing a role, not licensed to use a primitive.**

## Adding something

1. Edit `tokens.json`
2. `node design/validate-tokens.mjs`
3. Mirror into `android/app/src/main/java/com/moviemate/app/ui/theme/`
4. `node design/gen-usage-table.mjs` if you touched a colour
5. Record it in the changelog — `docs/MovieMate-Design-System.md` §16

Full rules and rationale: [`docs/MovieMate-Design-System.md`](../docs/MovieMate-Design-System.md)
