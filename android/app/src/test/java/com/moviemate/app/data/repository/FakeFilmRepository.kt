package com.moviemate.app.data.repository

import com.moviemate.app.data.model.Film

/**
 * An in-memory [FilmRepository] for ViewModel tests. Keyed by `tmdbId`,
 * matching [FirebaseFilmRepository.getFilms]'s own `associateBy { it.tmdbId }`
 * — callers look films up by a `WatchlistItem.filmId`/`Rating.filmId` that is
 * the same identifier as `Film.tmdbId` by convention across the schema.
 */
class FakeFilmRepository : FilmRepository {
    private val films = mutableMapOf<String, Film>()

    fun put(film: Film) {
        films[film.tmdbId] = film
    }

    override suspend fun getFilm(filmId: String): Film? = films[filmId]

    override suspend fun getFilms(filmIds: List<String>): Map<String, Film> =
        films.filterKeys { it in filmIds }
}
