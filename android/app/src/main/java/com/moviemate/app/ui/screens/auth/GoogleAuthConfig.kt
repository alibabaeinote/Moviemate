package com.moviemate.app.ui.screens.auth

/**
 * The OAuth 2.0 **Web** client ID Google Sign-In authenticates against.
 *
 * Not the Android client ID from google-services.json — Credential Manager's
 * [com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption]
 * takes the Web one, because that is the client Firebase Auth verifies the ID
 * token against on the backend, regardless of which platform the token came
 * from.
 *
 * PLACEHOLDER: there is no real Firebase project behind this build yet (see
 * android/app/google-services.ci.json). Once one exists, enable Google as a
 * sign-in provider in the Firebase console, then copy the "Web client" ID it
 * generates from Google Cloud Console → APIs & Services → Credentials into
 * this constant. Sign-in will fail with an unhelpful "wrong audience" style
 * error until this is a real ID.
 */
object GoogleAuthConfig {
    const val WEB_CLIENT_ID = "REPLACE_WITH_FIREBASE_WEB_CLIENT_ID.apps.googleusercontent.com"
}
