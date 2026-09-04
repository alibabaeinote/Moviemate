package com.moviemate.app.ui.screens.onboarding

/**
 * Client-side mirrors of the onboarding numbers.
 *
 * The server is the authority — `onRatingComplete` decides who has finished
 * onboarding by counting against `ALGORITHM_CONFIG.onboardingRatingTarget`.
 * These values only drive the progress copy and how big a deck to ask for. If
 * the server's target changes and this does not, the UI will say "10 of 10"
 * while the user is not actually done, so keep them in step.
 *
 * @see functions/src/config/algorithm.ts
 * @see functions/src/config/product.ts
 */
object OnboardingConfig {

    /** Mirrors ALGORITHM_CONFIG.onboardingRatingTarget. */
    const val RATING_TARGET = 10

    /**
     * Bigger than the server's default deck of 15, and bigger than
     * [RATING_TARGET], because people cannot rate films they have not seen.
     * Without headroom to skip, someone with narrow taste hits the end of the
     * deck still short of the target and is stuck. Capped at 30 server-side.
     */
    const val DECK_SIZE = 20

    /**
     * Genres to pick before the deck is built.
     *
     * One is enough to shape the deck; asking for three up front is a wall in
     * front of a person who has not seen anything yet.
     */
    const val MIN_GENRES = 1

    /** The Taste Dial opens here — neutral, so the first drag is a real choice. */
    const val DEFAULT_SCORE = 50f
}
