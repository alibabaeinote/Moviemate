package com.moviemate.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Firestore document models.
 *
 * These mirror functions/src/types.ts exactly — when one side changes, change
 * the other. Defaults are required: Firestore's reflective deserializer needs a
 * no-arg constructor.
 */

data class NotificationSettings(
    val dailyMatch: Boolean = true,
    val partnerActivity: Boolean = true,
    val reminders: Boolean = true,
)

data class User(
    @DocumentId val id: String = "",
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val emailVerified: Boolean = false,
    val createdAt: Timestamp? = null,
    val pairId: String? = null,
    val onboardingComplete: Boolean = false,
    val ratingCount: Int = 0,
    val fcmTokens: List<String> = emptyList(),
    val fcmTokenUpdatedAt: Timestamp? = null,
    val notificationSettings: NotificationSettings = NotificationSettings(),
    val timezone: String = "UTC",
    val lastActiveAt: Timestamp? = null,
)

data class Pair(
    @DocumentId val id: String = "",
    val userA: String = "",
    val userB: String? = null,
    val inviteCode: String = "",
    val inviteCodeExpiresAt: Timestamp? = null,
    val status: String = "waiting_partner",
    val createdAt: Timestamp? = null,
    @get:PropertyName("aBothOnboarded")
    @set:PropertyName("aBothOnboarded")
    var aBothOnboarded: Boolean = false,
    val streakCount: Int = 0,
    val lastMatchGeneratedAt: Timestamp? = null,
    val lastWatchAt: Timestamp? = null,
    val timezone: String = "UTC",
)

/**
 * Score is a continuous 0-100 Taste Dial value. It is a Double, never an Int
 * in 1..5 — see PRD §7.3 and the warning in the backend schema doc.
 */
data class Rating(
    @DocumentId val id: String = "",
    val userId: String = "",
    val filmId: String = "",
    val score: Double = 0.0,
    val isInitialOnboarding: Boolean = false,
    val reactionEmoji: String? = null,
    val ratedAt: Timestamp? = null,
)

data class CommitStatus(
    val userA: Boolean = false,
    val userB: Boolean = false,
)

data class ShortlistEntry(
    val filmId: String = "",
    val score: Int = 0,
    val reason: String = "",
)

data class Match(
    @DocumentId val id: String = "",
    val filmId: String = "",
    val score: Int = 0,
    val reason: String = "",
    val suggestedAt: Timestamp? = null,
    val status: String = "suggested",
    val attemptNumber: Int = 1,
    val commitStatus: CommitStatus = CommitStatus(),
    val bothConfirmedAt: Timestamp? = null,
    val watchedConfirmedAt: Timestamp? = null,
    val shortlist: List<ShortlistEntry> = emptyList(),
    val noMatchesReason: String? = null,
    val fallbackUnlocked: Boolean = false,
    val scheduledFor: Timestamp? = null,
)

data class WatchlistItem(
    @DocumentId val id: String = "",
    val filmId: String = "",
    val addedBy: String = "",
    val addedAt: Timestamp? = null,
    val source: String = "manual_search",
    val status: String = "waiting",
    val commitStatus: CommitStatus = CommitStatus(),
    val watchedAt: Timestamp? = null,
    val mutualScore: Double? = null,
)

data class Film(
    @DocumentId val id: String = "",
    val tmdbId: String = "",
    val title: String = "",
    val posterPath: String? = null,
    val genres: List<String> = emptyList(),
    val releaseYear: Int = 0,
    val runtime: Int = 0,
    val overview: String = "",
    val tmdbRating: Double = 0.0,
    val countries: List<String> = emptyList(),
    val cachedAt: Timestamp? = null,
    val expiresAt: Timestamp? = null,
)

/** The three Watchlist buckets from PRD §7.4. */
enum class WatchlistBucket(val label: String, val status: String) {
    Ready("Ready to watch", "ready"),
    Waiting("Waiting on you", "waiting"),
    Watched("Watched", "watched"),
}
