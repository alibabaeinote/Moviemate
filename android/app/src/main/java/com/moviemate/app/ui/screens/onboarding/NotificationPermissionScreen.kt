package com.moviemate.app.ui.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.moviemate.app.ui.components.PrimaryCta
import com.moviemate.app.ui.components.SecondaryCta
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Space

/**
 * Ask for POST_NOTIFICATIONS, with the reason first.
 *
 * The system dialog is one-shot per install — a decline is close to permanent —
 * so it is worth spending a screen explaining what the notification actually
 * is before spending the prompt. The daily match is the product; without the
 * notification most people simply never open the app that evening.
 *
 * Below Android 13 there is no runtime permission to request, so the screen
 * skips itself rather than showing a button that does nothing.
 */
@Composable
fun NotificationPermissionScreen(onDone: () -> Unit) {
    val colors = MovieMateTheme.colors
    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // Declining is a valid answer. The app still works — it just has to be
        // opened deliberately — so onboarding continues either way.
        onDone()
    }

    LaunchedEffect(needsPermission) { if (!needsPermission) onDone() }

    if (!needsPermission) return

    OnboardingScaffold {
        Text("ONE PICK A NIGHT", style = MovieMateType.megaHeadline, color = colors.textPrimary)
        Text(
            "We send one notification a day, when your match is ready. " +
                "That's the whole thing — no streak nags, no re-engagement pokes.",
            style = MovieMateType.body,
            color = colors.textSecondary,
        )

        Spacer(Modifier.height(Space.sectionGap))

        PrimaryCta(
            label = "Turn on notifications",
            onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        )
        SecondaryCta(label = "Not now", onClick = onDone)
    }
}