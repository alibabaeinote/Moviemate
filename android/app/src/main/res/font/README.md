# Fonts

Type.kt expects these files here. They are not committed — download them from
Google Fonts and drop them in with exactly these names (Android resource names
must be lowercase, letters/digits/underscore only):

Big Shoulders Display — https://fonts.google.com/specimen/Big+Shoulders+Display
- `big_shoulders_display_bold.ttf`      (700)
- `big_shoulders_display_extrabold.ttf` (800)
- `big_shoulders_display_black.ttf`     (900)

Inter — https://fonts.google.com/specimen/Inter
- `inter_regular.ttf`   (400)
- `inter_medium.ttf`    (500)
- `inter_semibold.ttf`  (600)
- `inter_bold.ttf`      (700)
- `inter_extrabold.ttf` (800)

Both are SIL Open Font License 1.1, so shipping them inside the APK is fine.

Substituting a different display face is not a free choice: Archivo Black and
Anton were both tried and rejected (too wide, and unreadable at small sizes
respectively). Big Shoulders is drawn condensed rather than squeezed, which is
why it survives at 12sp. See docs/MovieMate-Design-System.md §3.
