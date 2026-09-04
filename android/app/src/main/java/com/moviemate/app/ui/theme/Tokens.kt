package com.moviemate.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * TIER 1 — Primitives.
 *
 * Mirrors `ref.*` in design/tokens.json, which is the source of truth. Change a
 * value there first, run `node design/validate-tokens.mjs`, then mirror it here.
 *
 * Nothing outside this file's sibling [MovieMateColorScheme] may reference these
 * directly: screens and components read semantic roles, never primitives. A
 * primitive used in a screen is how a palette change turns into a week of
 * find-and-replace.
 *
 * Naming: neutral and factual (`Blue600`), never role-based (`ButtonColor`).
 * Numbers ascend with darkness for inks and with lightness for accents, so a
 * new step can be slotted in without renumbering.
 */
internal object Ref {

    // ---- Palette: ink (dark greys / near-blacks) ----
    val Ink0 = Color(0xFF0F0F12)   // deepest ground, slightly cool
    val Ink1 = Color(0xFF17171B)   // raised surface
    val Ink2 = Color(0xFF1F1F25)   // sunken / track
    val Ink3 = Color(0xFF2A2A32)   // hairline
    val Ink500 = Color(0xFF696963) // secondary text on light (corrected in v9)
    val Ink600 = Color(0xFF8A8A85)
    val Ink700 = Color(0xFF9B9B96) // secondary text on dark
    val Ink900 = Color(0xFF101012) // primary text on light

    // ---- Palette: paper (lights) ----
    val Paper0 = Color(0xFFFFFFFF)
    val Paper50 = Color(0xFFF1F0EC)  // v8 background; v9 primary text on dark
    val Paper100 = Color(0xFFEFEEE9)
    val Paper200 = Color(0xFFDAD8D0)

    // ---- Palette: blue ----
    val Blue300 = Color(0xFFA3ACFF)
    val Blue400 = Color(0xFF7C89FF)  // text/icons on dark
    val Blue500 = Color(0xFF2937F0)  // hover
    val Blue600 = Color(0xFF1F2FE3)  // fill
    val Blue700 = Color(0xFF1826B8)  // pressed
    val BlueWashLight = Color(0xFFE4E7FB)
    val BlueWashDark = Color(0xFF1B1E3A)
    val BlueHairDark = Color(0xFF2A3059)

    // ---- Palette: lime ----
    val Lime400 = Color(0xFFD6F04E)
    val Lime500 = Color(0xFFCCE83B)

    // ---- Palette: coral ----
    val Coral500 = Color(0xFFFF6A46)

    // ---- Palette: amber (outstanding-task inset) ----
    val AmberGround = Color(0xFF1C1A12)
    val AmberHair = Color(0xFF3A3520)

    // ---- Dimension: 4px base grid ----
    val D0 = 0.dp
    val D1 = 4.dp
    val D2 = 8.dp
    val D3 = 12.dp
    val D4 = 16.dp
    val D5 = 20.dp
    val D6 = 28.dp
    val D7 = 36.dp
    val D8 = 56.dp

    // ---- Radius ----
    val RadiusChip = 12.dp
    val RadiusCard = 24.dp
    val RadiusHero = 28.dp
    val RadiusPill = 999.dp

    // ---- Motion ----
    const val DurationInstant = 120
    const val DurationQuick = 160
    const val DurationSettle = 280  // ceiling: every micro-interaction stays under 300ms
    const val ScalePress = 0.97f

    // ---- Opacity ----
    const val OpacityGrain = 0.04f     // 3-5% reads as warmth; 20% reads as a 2014 filter
    const val OpacityDisabled = 0.4f
    const val OpacityPressWash = 0.06f

    // ---- Border widths (colour is a separate semantic role) ----
    val BorderHairline = 1.dp
    val BorderThin = 1.5.dp
    val BorderThick = 2.5.dp
    val BorderFocus = 2.dp

    // ---- Breakpoints: Material 3 window size classes ----
    val BreakpointCompact = 0.dp      // phone portrait — the only class v1 ships
    val BreakpointMedium = 600.dp     // unfolded foldable, tablet portrait
    val BreakpointExpanded = 840.dp   // tablet landscape

    // ---- Layout grid (distinct from the 4dp spacing base) ----
    const val GridColumnsCompact = 4
    const val GridColumnsMedium = 8
    const val GridColumnsExpanded = 12
    val GridGutterCompact = 16.dp
    val GridGutterMedium = 24.dp
    val GridGutterExpanded = 24.dp

    // ---- Elevation ----
    // On dark, depth reads through surface lightness more than shadow: a black
    // shadow on a near-black ground is invisible.
    val Elevation0 = 0.dp
    val Elevation1 = 1.dp
    val Elevation2 = 8.dp
    val Elevation3 = 16.dp

    // ---- Icon ----
    val IconSm = 16.dp
    val IconMd = 21.dp
    val IconLg = 24.dp
    val IconXl = 32.dp
    val IconStrokeThin = 1.75.dp
    val IconStroke = 2.dp

    // ---- Density ----
    val RowComfortable = 72.dp
    val RowCompact = 56.dp
    val TouchMin = 48.dp
}
