package com.moviemate.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.moviemate.app.di.AppGraph
import com.moviemate.app.di.DefaultAppGraph

class MovieMateApplication : Application() {

    /**
     * Built once, here, because the repositories hold Firebase listeners and a
     * second set of them would mean paying twice for every snapshot.
     */
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        graph = DefaultAppGraph(this)
        MovieMateMessagingService.ensureChannels(this)
        refreshFcmToken()
    }

    /**
     * Re-register the FCM token on every launch, not just at sign-up: tokens
     * rotate on reinstall and on "clear data", and a stale token means silent
     * delivery failures (Notification Architecture §2).
     */
    private fun refreshFcmToken() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            FirebaseFirestore.getInstance().collection("users").document(uid).update(
                mapOf(
                    "fcmTokens" to FieldValue.arrayUnion(token),
                    "fcmTokenUpdatedAt" to Timestamp.now(),
                    "lastActiveAt" to Timestamp.now(),
                ),
            )
        }
    }
}
