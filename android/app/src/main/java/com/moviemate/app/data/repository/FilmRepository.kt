package com.moviemate.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.moviemate.app.data.model.Film
import kotlinx.coroutines.tasks.await

/**
 * Film metadata reads.
 *
 * Clients read filmCache and never write it — the rules forbid it, and the
 * 6-month TTL is enforced server-side. A cache miss here means the Cloud
 * Functions have not pulled that film yet.
 */
class FilmRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun getFilm(filmId: String): Film? = runCatching {
        firestore.collection("filmCache").document(filmId).get().await()
            .toObject(Film::class.java)
    }.getOrNull()

    suspend fun getFilms(filmIds: List<String>): Map<String, Film> {
        if (filmIds.isEmpty()) return emptyMap()
        // whereIn is capped at 30 values per query.
        return filmIds.distinct().chunked(30).flatMap { chunk ->
            runCatching {
                firestore.collection("filmCache")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                    .get().await()
                    .toObjects(Film::class.java)
            }.getOrDefault(emptyList())
        }.associateBy { it.tmdbId }
    }
}
