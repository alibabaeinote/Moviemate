package com.moviemate.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colour tokens from Design System v8 §2 — locked.
 *
 * Rule: at most two accents on a screen (Blue dominant, Lime for tags and
 * decoration). Coral is decorative only and must never sit on anything
 * tappable.
 */
object MovieMateColors {
    /** Warm grey. Never pure white. */
    val Background = Color(0xFFF1F0EC)

    /** Primary text — near-black. */
    val Ink = Color(0xFF101012)

    /** Supporting text and meta lines. */
    val InkSecondary = Color(0xFF6E6E68)

    /** Primary CTA, active elements, avatar rings, stat numbers. */
    val Blue = Color(0xFF1F2FE3)

    /** Pressed/active state for the primary button. */
    val BlueDark = Color(0xFF1826B8)

    /** Active bottom-nav pill background. */
    val BlueSoft = Color(0xFFE4E7FB)

    /** Second accent: tags and decoration only. */
    val Lime = Color(0xFFCCE83B)

    /** Decorative only — never on an interactive element. */
    val Coral = Color(0xFFFF6A46)

    /** Card and bottom-bar surface. */
    val Paper = Color(0xFFFFFFFF)

    val OnBlue = Color(0xFFFFFFFF)
    val OnLime = Ink
}
