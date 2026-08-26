package com.moviemate.app.data.remote

import com.moviemate.app.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Client-side TMDB access.
 *
 * Only the Watchlist manual-search flow calls TMDB directly. Everything else
 * reads filmCache in Firestore, which the Cloud Functions keep populated — that
 * is what keeps us inside TMDB's rate limit and its 6-month caching rule.
 *
 * Required attribution is rendered on the About row of the Us screen.
 */
object TmdbApi {
    const val ATTRIBUTION =
        "This product uses the TMDB API but is not endorsed or certified by TMDB."

    private const val BASE_URL = "https://api.themoviedb.org/3"
    private const val IMAGE_BASE = "https://image.tmdb.org/t/p"

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    /** Poster URL for a TMDB poster_path. `size` is a TMDB image size token. */
    fun posterUrl(posterPath: String?, size: String = "w500"): String? =
        posterPath?.let { "$IMAGE_BASE/$size$it" }

    suspend fun searchMovies(query: String): Result<List<TmdbMovie>> = withContext(Dispatchers.IO) {
        runCatching {
            require(BuildConfig.TMDB_ACCESS_TOKEN.isNotBlank()) {
                "TMDB_ACCESS_TOKEN is not set — see android/local.properties.example"
            }

            val url = "$BASE_URL/search/movie" +
                "?query=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                "&include_adult=false&language=en-US&page=1"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${BuildConfig.TMDB_ACCESS_TOKEN}")
                .header("accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "TMDB search failed: ${response.code}" }
                val body = response.body?.string().orEmpty()
                json.decodeFromString<TmdbSearchResponse>(body).results
            }
        }
    }
}

@Serializable
data class TmdbSearchResponse(
    val page: Int = 1,
    val results: List<TmdbMovie> = emptyList(),
)

@Serializable
data class TmdbMovie(
    val id: Int,
    val title: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    val overview: String = "",
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("vote_average") val voteAverage: Double = 0.0,
) {
    val releaseYear: Int get() = releaseDate.take(4).toIntOrNull() ?: 0
}
