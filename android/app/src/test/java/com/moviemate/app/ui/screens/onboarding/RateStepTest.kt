package com.moviemate.app.ui.screens.onboarding

import com.moviemate.app.data.repository.DeckFilm
import com.moviemate.app.data.repository.TmdbGenre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two steps of the rating deck, read as data rather than driven through the
 * ViewModel — [RateStep.RateDeck.exhausted] in particular guards a real bug:
 * running out of films is only "exhausted" if the target has not already been
 * reached, or a deck that finished exactly on the last card would incorrectly
 * offer "show me more films".
 */
class RateStepTest {

    private fun film(id: String) = DeckFilm(
        filmId = id,
        title = id,
        posterPath = null,
        genres = emptyList(),
        releaseYear = 2020,
        overview = "",
    )

    @Test
    fun `picking fewer than the minimum genres cannot continue`() {
        val step = RateStep.PickGenres(
            genres = listOf(TmdbGenre(1, "Drama")),
            selected = emptySet(),
        )
        assertFalse(step.canContinue)
    }

    @Test
    fun `one genre is enough once MIN_GENRES is met`() {
        val step = RateStep.PickGenres(
            genres = listOf(TmdbGenre(1, "Drama")),
            selected = setOf(1),
        )
        assertTrue(step.canContinue)
    }

    @Test
    fun `remaining counts down to the target, never below zero`() {
        val step = RateStep.RateDeck(
            films = listOf(film("f1")),
            index = 0,
            score = 50f,
            recorded = OnboardingConfig.RATING_TARGET - 1,
            genreIds = listOf(1),
        )
        assertEquals(1, step.remaining)

        // Defensive: recorded should never exceed the target in practice, but a
        // negative "remaining" would read as a deck that needs to grow, not
        // shrink.
        val overshot = step.copy(recorded = OnboardingConfig.RATING_TARGET + 3)
        assertEquals(0, overshot.remaining)
    }

    @Test
    fun `a deck with cards left is not exhausted regardless of remaining`() {
        val step = RateStep.RateDeck(
            films = listOf(film("f1"), film("f2")),
            index = 0,
            score = 50f,
            recorded = 0,
            genreIds = listOf(1),
        )
        assertFalse(step.exhausted)
    }

    @Test
    fun `running out of cards short of the target is exhausted`() {
        val step = RateStep.RateDeck(
            films = listOf(film("f1")),
            index = 1, // past the last card
            score = 50f,
            recorded = OnboardingConfig.RATING_TARGET - 1,
            genreIds = listOf(1),
        )
        assertTrue(step.exhausted)
    }

    /**
     * "Runs out before the target does" is the whole contract — running out on
     * the exact card that reaches the target is a finished deck, not one that
     * needs topping up.
     */
    @Test
    fun `running out of cards exactly at the target is not exhausted`() {
        val step = RateStep.RateDeck(
            films = listOf(film("f1")),
            index = 1,
            score = 50f,
            recorded = OnboardingConfig.RATING_TARGET,
            genreIds = listOf(1),
        )
        assertFalse(step.exhausted)
    }
}
