package com.moviemate.app.data.repository

import com.google.firebase.auth.FirebaseUser
import com.moviemate.app.data.model.NotificationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** One `updateNotificationSettings` call, recorded for assertions. */
data class UpdateNotificationSettingsCall(val uid: String, val settings: NotificationSettings)

/** One `updateProfile` call, recorded for assertions. */
data class UpdateProfileCall(val uid: String, val name: String, val avatarUrl: String?)

/**
 * An in-memory [AuthRepository] for ViewModel tests. [currentUser] and
 * [authState] stay null throughout: nothing under test needs a real
 * `FirebaseUser` — a ViewModel reads "who is signed in" from
 * [com.moviemate.app.data.session.SessionStore] (backed by
 * [com.moviemate.app.data.session.FakeSessionStore] in tests), never from
 * this repository directly.
 */
class FakeAuthRepository : AuthRepository {
    override val currentUser: FirebaseUser? = null

    override fun authState(): Flow<FirebaseUser?> = MutableStateFlow(null)

    var signInResult: Result<FirebaseUser>? = null
    var updateProfileResult: Result<Unit> = Result.success(Unit)
    var uploadAvatarResult: Result<String> = Result.success("https://example.com/avatar.jpg")

    val updatedNotificationSettings = mutableListOf<UpdateNotificationSettingsCall>()
    val updatedProfiles = mutableListOf<UpdateProfileCall>()
    var signedOut = false

    override suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> =
        signInResult ?: error("signInResult was not configured for this test")

    override suspend fun updateProfile(uid: String, name: String, avatarUrl: String?): Result<Unit> {
        updatedProfiles.add(UpdateProfileCall(uid, name, avatarUrl))
        return updateProfileResult
    }

    override suspend fun updateNotificationSettings(
        uid: String,
        settings: NotificationSettings,
    ): Result<Unit> {
        updatedNotificationSettings.add(UpdateNotificationSettingsCall(uid, settings))
        return Result.success(Unit)
    }

    override suspend fun uploadAvatar(uid: String, bytes: ByteArray): Result<String> = uploadAvatarResult

    override fun signOut() {
        signedOut = true
    }
}
