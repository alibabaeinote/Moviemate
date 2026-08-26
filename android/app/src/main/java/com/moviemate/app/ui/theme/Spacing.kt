package com.moviemate.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing scale from Design System v8 §4 — locked.
 *
 * Every padding, margin and gap must come from this scale. No in-between
 * values.
 */
object Spacing {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 28.dp
    val s7 = 36.dp
}

/** Corner radii from Design System v8 §4. */
object Radius {
    /** Small inline chips. */
    val chip = 12.dp

    /** Standard card. */
    val card = 24.dp

    /** Large / hero card. */
    val hero = 28.dp

    /** Pills: buttons, tags, avatars, the nav bar. */
    val pill = 999.dp
}
