package com.moviemate.app.data.remote

/**
 * TMDB constants shared by the client.
 *
 * The client never calls TMDB directly — search, discovery and caching all run
 * server-side (see functions/src/callable/searchFilms.ts and tmdb/client.ts),
 * which keeps the bearer token off the device and keeps every request inside
 * TMDB's rate limit and 6-month caching rule. All film data reaches the client
 * through filmCache in Firestore.
 *
 * What remains here is display-only: building a poster URL from the path
 * filmCache already stores, and the attribution TMDB's terms require.
 */
object TmdbApi {
    const val ATTRIBUTION =
        "This product uses the TMDB API but is not endorsed or certified by TMDB."

    private const val IMAGE_BASE = "https://image.tmdb.org/t/p"

    /** Poster URL for a TMDB poster_path. `size` is a TMDB image size token. */
    fun posterUrl(posterPath: String?, size: String = "w500"): String? =
        posterPath?.let { "$IMAGE_BASE/$size$it" }
}
