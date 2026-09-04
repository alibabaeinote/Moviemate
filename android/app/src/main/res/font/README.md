# Fonts

Both families ship with the repo — nothing to download.

| File | Family | Licence |
|---|---|---|
| `inter_variable.ttf` | Inter | SIL OFL 1.1 |
| `big_shoulders_display_variable.ttf` | Big Shoulders Display | SIL OFL 1.1 |

Licence texts are in `app/src/main/assets/licenses/`, which is what the OFL
requires when the fonts are redistributed inside the APK.

## Why variable fonts

Google Fonts now publishes only the variable cuts of both families, and minSdk
26 supports them. Two files instead of eight, and any weight in the range is
reachable rather than only the ones that happened to be downloaded.

A variable font ignores `FontWeight` on its own — the weight also has to be
passed as a variation axis. `Type.kt` does that via `FontVariation.Settings`.
Declaring only the `FontWeight` would silently render every style at the
default instance, which looks like the font "not working".

## Substituting the display face is not a free choice

Archivo Black (too wide) and Anton (unreadable at small sizes) were both tried
and rejected. Big Shoulders is drawn condensed rather than squeezed, which is
why it survives at 12sp. See `docs/MovieMate-Design-System.md` §5.
