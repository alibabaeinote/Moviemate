package com.moviemate.app.ui.screens.onboarding

import android.content.Context
import android.content.Intent

/**
 * Hand the invite code to whatever the user already talks to their partner in.
 *
 * A system share sheet rather than a hard-coded messenger: the one person this
 * code is for is already in a conversation with them somewhere, and the app has
 * no business guessing where.
 */
fun Context.shareInviteCode(code: String) {
    val text = "Watch something with me on MovieMate. My invite code is $code."
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(send, null))
}
