# Fonts

> This lives here rather than in `app/src/main/res/font/` because Android's
> resource merger rejects any file in a `res/font/` directory that is not
> `.xml`, `.ttf`, `.ttc` or `.otf` — a README there fails the build outright.

Both families ship with the repo — nothing to download.

| File | Family | Licence |
|---|---|---|
| `plus_jakarta_sans_variable.ttf` | Plus Jakarta Sans | SIL OFL 1.1 |
| `space_grotesk_variable.ttf` | Space Grotesk | SIL OFL 1.1 |

The font files themselves are in `app/src/main/res/font/`. Licence texts are
in `app/src/main/assets/licenses/`, which is what the OFL
requires when the fonts are redistributed inside the APK.

## Why variable fonts

Google Fonts now publishes only the variable cuts of both families, and minSdk
26 supports them. Two files instead of eight, and any weight in the range is
reachable rather than only the ones that happened to be downloaded.

A variable font ignores `FontWeight` on its own — the weight also has to be
passed as a variation axis. `Type.kt` does that via `FontVariation.Settings`.
Declaring only the `FontWeight` would silently render every style at the
default instance, which looks like the font "not working".

## v10 pairing

Big Shoulders Display and Inter (v9) were replaced together: the whole point
of the new pairing is that weight alone stopped being enough contrast once
the palette dropped to a single accent, so headline and body now come from
two visually distinct families rather than two weights of a related one.
Space Grotesk's variable axis tops out at 700 — no headline role asks for
800/900 anymore, see `Type.kt`. See `docs/MovieMate-Design-System.md` §5.
