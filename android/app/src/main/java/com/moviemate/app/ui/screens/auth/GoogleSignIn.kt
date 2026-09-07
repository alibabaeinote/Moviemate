package com.moviemate.app.ui.screens.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Runs the system "Sign in with Google" sheet and returns the ID token
 * Firebase Auth needs.
 *
 * [GetSignInWithGoogleOption] rather than the auto-sign-in [GetGoogleIdOption]:
 * this fires from an explicit "Continue with Google" tap, so it should always
 * show the account picker, never silently pick a previously-used account.
 */
suspend fun requestGoogleIdToken(context: Context): Result<String> = runCatching {
    val option = GetSignInWithGoogleOption.Builder(GoogleAuthConfig.WEB_CLIENT_ID).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

    val response = CredentialManager.create(context).getCredential(context, request)
    val credential = response.credential

    require(
        credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
    ) { "Expected a Google ID token credential, got ${credential.type}" }

    GoogleIdTokenCredential.createFrom(credential.data).idToken
}
