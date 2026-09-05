package com.moviemate.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.moviemate.app.data.model.NotificationSettings
import com.moviemate.app.data.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.TimeZone

/**
 * Firebase Auth — Email/Password only in v1 (Backend Schema §1).
 * Social login is deferred, not dropped.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    val currentUser: FirebaseUser? get() = auth.currentUser

    /** Emits on every sign-in/sign-out so the app keeps the user logged in across launches. */
    fun authState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Create the account, send the verification mail, and seed users/{uid}.
     *
     * The seeded document must match what the security rules allow on create:
     * pairId null, onboardingComplete false, ratingCount 0.
     */
    suspend fun signUp(name: String, email: String, password: String): Result<FirebaseUser> =
        runCatching {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = requireNotNull(result.user) { "Sign up returned no user" }

            user.sendEmailVerification().await()

            val profile = User(
                uid = user.uid,
                name = name.trim(),
                email = email.trim(),
                emailVerified = false,
                createdAt = Timestamp.now(),
                pairId = null,
                onboardingComplete = false,
                ratingCount = 0,
                notificationSettings = NotificationSettings(),
                timezone = TimeZone.getDefault().id,
            )
            firestore.collection("users").document(user.uid).set(profile).await()
            user
        }

    suspend fun signIn(email: String, password: String): Result<FirebaseUser> = runCatching {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        requireNotNull(result.user) { "Sign in returned no user" }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun reloadVerificationState(): Boolean {
        val user = auth.currentUser ?: return false
        user.reload().await()
        return user.isEmailVerified
    }

    /**
     * Notification preferences.
     *
     * `notificationSettings` is one of the six fields the security rules let a
     * user write on their own document; pairId, onboardingComplete and
     * ratingCount are owned by Cloud Functions and a write here would be
     * rejected outright.
     */
    suspend fun updateNotificationSettings(
        uid: String,
        settings: NotificationSettings,
    ): Result<Unit> = runCatching {
        firestore.collection("users").document(uid)
            .update("notificationSettings", settings)
            .await()
    }

    fun signOut() = auth.signOut()
}
