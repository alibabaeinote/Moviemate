package com.moviemate.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviemate.app.di.LocalAppGraph
import com.moviemate.app.ui.components.PrimaryCta
import com.moviemate.app.ui.core.ActionState
import com.moviemate.app.ui.core.factoryOf
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Space

/**
 * The entire signed-out experience: one screen, one button.
 *
 * Google Sign-In replaced Email/Password rather than sitting alongside it, so
 * there is nothing here to route between — no separate sign-up, sign-in or
 * forgot-password screen. AuthRepository.signInWithGoogle handles a first-time
 * and a returning account identically; RoutingScreen (reached after success)
 * is what decides whether that account lands in onboarding or on tonight's
 * match.
 */
@Composable
fun WelcomeScreen(onSignedIn: () -> Unit) {
    val graph = LocalAppGraph.current
    val viewModel: WelcomeViewModel = viewModel(
        factory = factoryOf { WelcomeViewModel(graph.authRepository) },
    )
    val action by viewModel.action.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = MovieMateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceGround)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screenGutter, vertical = Space.screenTop),
        verticalArrangement = Arrangement.spacedBy(Space.stack),
    ) {
        Text("MOVIEMATE", style = MovieMateType.megaHeadline, color = colors.textPrimary)
        Text(
            "One film a night, picked for both of you.",
            style = MovieMateType.body,
            color = colors.textSecondary,
        )

        Spacer(Modifier.height(Space.sectionGap))

        PrimaryCta(
            label = if (action.isRunning) "Signing in…" else "Continue with Google",
            enabled = !action.isRunning,
            onClick = { viewModel.continueWithGoogle(context, onSuccess = onSignedIn) },
        )

        (action as? ActionState.Failed)?.let {
            Text(it.message, style = MovieMateType.meta, color = colors.statusDecorative)
        }
    }
}
