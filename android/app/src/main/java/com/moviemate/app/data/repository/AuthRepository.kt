package com.moviemate.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.moviemate.app.data.model.NotificationSettings
import com.moviemate.app.data.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.TimeZone

/**
 * Firebase Auth — Google Sign-In only.
 *
 * v1 shipped Email/Password (Backend Schema §1); this replaced it rather than
 * adding to it, so there is exactly one account-recovery story instead of two.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
) {
    val currentUser: FirebaseUser? get() = auth.currentUser

    /** Emits on every sign-in/sign-out so the app keeps the user logged in across launches. */
    fun authState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Exchange a Google ID token (from Credential Manager — see
     * ui/screens/auth/GoogleSignIn.kt) for a Firebase session, seeding
     * users/{uid} on the account's first sign-in only.
     *
     * `additionalUserInfo.isNewUser` is what Firebase itself just decided this
     * was, straight off the sign-in call — reading it is free. Checking
     * whether the Firestore document already exists would be a second round
     * trip to answer a question Firebase already answered.
     */
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        val user = requireNotNull(result.user) { "Google sign-in returned no user" }

        if (result.additionalUserInfo?.isNewUser == true) {
            val profile = User(
                uid = user.uid,
                // The seed here is a starting point, not a lock: the profile
                // screen can rename and re-picture from the moment they land.
                name = user.displayName?.trim().orEmpty(),
                email = user.email.orEmpty(),
                avatarUrl = user.photoUrl?.toString(),
                createdAt = Timestamp.now(),
                pairId = null,
                onboardingComplete = false,
                ratingCount = 0,
                notificationSettings = NotificationSettings(),
                timezone = TimeZone.getDefault().id,
            )
            firestore.collection("users").document(user.uid).set(profile).await()
        }

        user
    }

    /**
     * Name and avatar together, so a profile save is one write rather than two
     * — two writes would mean onUserProfileUpdated (which denormalizes both
     * onto the pair document) fires and notifies twice for one edit.
     */
    suspend fun updateProfile(uid: String, name: String, avatarUrl: String?): Result<Unit> =
        runCatching {
            firestore.collection("users").document(uid)
                .update(mapOf("name" to name.trim(), "avatarUrl" to avatarUrl))
                .await()
        }

    /**
     * Upload the picked image and return its download URL.
     *
     * One fixed object per user (`avatars/{uid}/profile.jpg`) rather than a
     * new file per upload: storage.rules grants write only to that exact path,
     * and a re-upload overwriting it is what keeps an old avatar from lingering
     * in the bucket once nothing points at it any more.
     */
    suspend fun uploadAvatar(uid: String, bytes: ByteArray): Result<String> = runCatching {
        val ref = storage.reference.child("avatars/$uid/profile.jpg")
        ref.putBytes(bytes).await()
        ref.downloadUrl.await().toString()
    }

    fun signOut() = auth.signOut()
}
