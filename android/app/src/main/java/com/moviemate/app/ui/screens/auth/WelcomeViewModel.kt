package com.moviemate.app.ui.screens.auth

import android.content.Context
import com.moviemate.app.data.repository.AuthRepository
import com.moviemate.app.ui.core.MovieMateViewModel

class WelcomeViewModel(private val authRepository: AuthRepository) : MovieMateViewModel() {

    /**
     * One action covers both steps: the system account picker, then the
     * Firebase exchange. A failure at either step — the user backing out of
     * the picker, or the token exchange itself — surfaces the same way, on
     * the same button.
     */
    fun continueWithGoogle(context: Context, onSuccess: () -> Unit) {
        runAction(onSuccess = onSuccess) {
            requestGoogleIdToken(context).fold(
                onSuccess = { idToken -> authRepository.signInWithGoogle(idToken) },
                onFailure = { Result.failure(it) },
            )
        }
    }
}
