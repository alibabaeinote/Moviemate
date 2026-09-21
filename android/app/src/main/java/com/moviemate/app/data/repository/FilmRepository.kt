package com.moviemate.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.moviemate.app.data.model.Film
import com.moviemate.app.data.remote.TmdbClient
import kotlinx.coroutines.tasks.await

/**
 * Film metadata reads.
 *
 * Clients read filmCache and never write it — the rules forbid it, and the
 * 6-month TTL is enforced server-side. A cache miss there normally means the
 * Cloud Functions have not pulled that film yet; on this project, without
 * Blaze, filmCache is never written by anything at all, so every miss falls
 * through to [TmdbClient] instead (same no-Blaze fallback as the rest of the
 * client-side TMDB access — see TmdbClient's doc comment).
 *
 * An interface, not just a class, so ViewModel tests can substitute a fake
 * instead of talking to Firestore — see `data.repository.FakeFilmRepository`
 * in the test source set.
 */
interface FilmRepository {
    suspend fun getFilm(filmId: String): Film?
    suspend fun getFilms(filmIds: List<String>): Map<String, Film>
}

/** The real, Firestore-backed [FilmRepository], falling back to TMDB directly on a cache miss. */
class FirebaseFilmRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val tmdbClient: TmdbClient = TmdbClient(),
) : FilmRepository {
    override suspend fun getFilm(filmId: String): Film? {
        val cached = runCatching {
            firestore.collection("filmCache").document(filmId).get().await()
                .toObject(Film::class.java)
        }.getOrNull()
        return cached ?: tmdbClient.getFilm(filmId)
    }

    override suspend fun getFilms(filmIds: List<String>): Map<String, Film> {
        if (filmIds.isEmpty()) return emptyMap()
        val distinctIds = filmIds.distinct()

        // whereIn is capped at 30 values per query.
        val cached = distinctIds.chunked(30).flatMap { chunk ->
            runCatching {
                firestore.collection("filmCache")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                    .get().await()
                    .toObjects(Film::class.java)
            }.getOrDefault(emptyList())
        }.associateBy { it.tmdbId }

        val missingIds = distinctIds.filterNot { it in cached }
        if (missingIds.isEmpty()) return cached

        return cached + tmdbClient.getFilms(missingIds)
    }
}
