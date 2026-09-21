package com.moviemate.app.data.remote

import com.moviemate.app.BuildConfig
import com.moviemate.app.data.model.Film
import com.moviemate.app.data.repository.DeckFilm
import com.moviemate.app.data.repository.TmdbGenre
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Direct, client-side TMDB v3 calls — the no-Blaze fallback for onboarding
 * genres/films, Watchlist search, and the daily match's candidate pool and
 * film-detail lookups. Mirrors web/tmdb.js: TMDB's v3 `api_key` scheme is
 * designed for exactly this — client-side, no backend needed to hide it —
 * unlike the callables this replaces (listGenres, getOnboardingFilms,
 * searchFilms in PairRepository), which required Cloud Functions, which
 * require the Blaze plan to deploy at all.
 *
 * Also the no-Blaze fallback for FirebaseFilmRepository's filmCache reads:
 * filmCache is only ever written by Cloud Functions, so without Blaze it
 * stays permanently empty — every lookup there falls through to
 * [getFilm]/[getFilms] here instead.
 *
 * Deliberately minimal: OkHttp + org.json, not Retrofit + Gson/Moshi, for a
 * handful of endpoints that don't need either's machinery.
 */
class TmdbClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val apiKey: String = BuildConfig.TMDB_API_KEY,
) {
    /** Mirrors functions/src/config/product.ts's PRODUCT_CONFIG.minVoteCount. */
    private val minVoteCount = 200

    /** Mirrors functions/src/callable/onboardingFilms.ts's ERA_WINDOWS. */
    private data class EraWindow(val gte: String, val lte: String, val share: Double)

    private val eraWindows = listOf(
        EraWindow("2020-01-01", "2100-01-01", 0.4),
        EraWindow("2010-01-01", "2019-12-31", 0.3),
        EraWindow("2000-01-01", "2009-12-31", 0.15),
        EraWindow("1950-01-01", "1999-12-31", 0.15),
    )

    private suspend fun get(path: String, params: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            check(apiKey.isNotBlank()) {
                "TMDB_API_KEY is not set — add it to android/local.properties as " +
                    "TMDB_API_KEY=... (see web/tmdb-config.example.js for how to get a v3 key)."
            }
            val url = "https://api.themoviedb.org/3$path".toHttpUrl().newBuilder().apply {
                addQueryParameter("api_key", apiKey)
                addQueryParameter("language", "en-US")
                for ((key, value) in params) addQueryParameter(key, value)
            }.build()

            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    if (response.code == 401) {
                        throw IOException("TMDB rejected the API key — check android/local.properties.")
                    }
                    throw IOException("TMDB request failed (${response.code}).")
                }
                JSONObject(body)
            }
        }

    @Volatile
    private var cachedGenres: List<TmdbGenre>? = null

    /** Stage 1 of onboarding: the genre list. */
    suspend fun listGenres(): List<TmdbGenre> {
        cachedGenres?.let { return it }
        val data = get("/genre/movie/list")
        val array = data.getJSONArray("genres")
        val genres = (0 until array.length()).map {
            val g = array.getJSONObject(it)
            TmdbGenre(id = g.getInt("id"), name = g.getString("name"))
        }
        cachedGenres = genres
        return genres
    }

    private fun JSONObject.genreNames(genreNamesById: Map<Int, String>): List<String> {
        val ids = optJSONArray("genre_ids") ?: JSONArray()
        return (0 until ids.length()).mapNotNull { genreNamesById[ids.getInt(it)] }
    }

    private fun JSONObject.releaseYear(): Int {
        val date = optString("release_date", "")
        return if (date.length >= 4) date.take(4).toIntOrNull() ?: 0 else 0
    }

    private fun JSONObject.posterPath(): String? =
        if (isNull("poster_path")) null else optString("poster_path").ifEmpty { null }

    private fun JSONObject.toDeckFilm(genreNamesById: Map<Int, String>): DeckFilm = DeckFilm(
        filmId = getInt("id").toString(),
        title = optString("title", ""),
        posterPath = posterPath(),
        genres = genreNames(genreNamesById),
        releaseYear = releaseYear(),
        overview = optString("overview", ""),
        tmdbRating = optDouble("vote_average", 0.0),
    )

    /**
     * Stage 2: a deck spanning the chosen genres and the era windows above,
     * excluding anything already in [excludeIds]. Mirrors getOnboardingFilms
     * minus the filmCache write — no Cloud Functions to own that cache on
     * this path, so the client just re-queries TMDB, fine at this app's scale.
     */
    suspend fun getOnboardingFilms(
        genreIds: List<Int>,
        size: Int,
        excludeIds: Set<String> = emptySet(),
    ): List<DeckFilm> {
        val genreNamesById = listGenres().associate { it.id to it.name }
        val quotas = eraWindows.map { maxOf(1, Math.round(size * it.share).toInt()) }

        val buckets = eraWindows.mapIndexed { index, window ->
            val data = get(
                "/discover/movie",
                mapOf(
                    "with_genres" to genreIds.joinToString(","),
                    "vote_count.gte" to minVoteCount.toString(),
                    "sort_by" to "popularity.desc",
                    "primary_release_date.gte" to window.gte,
                    "primary_release_date.lte" to window.lte,
                ),
            )
            val results = data.optJSONArray("results") ?: JSONArray()
            val taken = mutableListOf<JSONObject>()
            for (i in 0 until results.length()) {
                if (taken.size >= quotas[index]) break
                val movie = results.getJSONObject(i)
                if (movie.getInt("id").toString() in excludeIds) continue
                val movieGenreIds = movie.optJSONArray("genre_ids")
                if (movieGenreIds == null || movieGenreIds.length() == 0) continue
                taken += movie
            }
            taken
        }

        // Round-robin the era buckets so the deck doesn't open with one era
        // in a block — a user who has seen only its most recent films has no
        // era signal to build a profile from.
        val depth = buckets.maxOfOrNull { it.size } ?: 0
        val interleaved = mutableListOf<JSONObject>()
        for (i in 0 until depth) {
            for (bucket in buckets) bucket.getOrNull(i)?.let { interleaved += it }
        }
        return interleaved.take(size).map { it.toDeckFilm(genreNamesById) }
    }

    /** Manual film search for the Watchlist. */
    suspend fun searchFilms(query: String): List<DeckFilm> {
        val genreNamesById = listGenres().associate { it.id to it.name }
        val data = get("/search/movie", mapOf("query" to query, "include_adult" to "false"))
        val results = data.optJSONArray("results") ?: JSONArray()
        return (0 until results.length()).map { results.getJSONObject(it).toDeckFilm(genreNamesById) }
    }

    /**
     * A broad, popularity-sorted candidate pool for the daily match — unlike
     * the onboarding deck, not biased toward any particular genre, mirroring
     * matchService.ts's buildCandidatePool (the taste-profile scoring is
     * what biases the result, not the pool itself).
     */
    suspend fun getMatchCandidates(size: Int, excludeIds: Set<String> = emptySet()): List<DeckFilm> {
        val genreNamesById = listGenres().associate { it.id to it.name }
        val pool = LinkedHashMap<String, DeckFilm>()
        val pages = maxOf(1, (size + 19) / 20)

        for (page in 1..pages) {
            val data = get(
                "/discover/movie",
                mapOf(
                    "sort_by" to "popularity.desc",
                    "vote_count.gte" to minVoteCount.toString(),
                    "page" to page.toString(),
                ),
            )
            val results = data.optJSONArray("results") ?: JSONArray()
            for (i in 0 until results.length()) {
                val movie = results.getJSONObject(i)
                val id = movie.getInt("id").toString()
                if (id in excludeIds || pool.containsKey(id)) continue
                val movieGenreIds = movie.optJSONArray("genre_ids")
                if (movieGenreIds == null || movieGenreIds.length() == 0) continue
                if (movie.optString("release_date", "").isEmpty()) continue
                pool[id] = movie.toDeckFilm(genreNamesById)
            }
            if (pool.size >= size) break
            if (page >= data.optInt("total_pages", 1)) break
        }
        return pool.values.toList()
    }

    /**
     * Full detail for one film, by id — used to rebuild a taste profile from
     * rating history and to resolve a match/watchlist filmId to a
     * displayable title/poster, both of which would normally come from
     * filmCache.
     */
    suspend fun getFilm(filmId: String): Film? = runCatching {
        val data = get("/movie/$filmId")
        val genresArray = data.optJSONArray("genres") ?: JSONArray()
        val genres = (0 until genresArray.length()).map { genresArray.getJSONObject(it).getString("name") }
        val countriesArray = data.optJSONArray("production_countries") ?: JSONArray()
        val countries = (0 until countriesArray.length()).map {
            countriesArray.getJSONObject(it).getString("iso_3166_1")
        }
        Film(
            id = filmId,
            tmdbId = filmId,
            title = data.optString("title", ""),
            posterPath = data.posterPath(),
            genres = genres,
            releaseYear = data.releaseYear(),
            runtime = data.optInt("runtime", 0),
            overview = data.optString("overview", ""),
            tmdbRating = data.optDouble("vote_average", 0.0),
            countries = countries,
        )
    }.getOrNull()

    /** [getFilm] for a set of ids, fetched in parallel. A film TMDB no longer has just drops out. */
    suspend fun getFilms(filmIds: List<String>): Map<String, Film> = coroutineScope {
        filmIds.distinct()
            .map { id -> async { id to getFilm(id) } }
            .map { it.await() }
            .mapNotNull { (id, film) -> film?.let { id to it } }
            .toMap()
    }
}
