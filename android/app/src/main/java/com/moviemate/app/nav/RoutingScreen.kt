package com.moviemate.app.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviemate.app.di.LocalAppGraph
import com.moviemate.app.ui.core.factoryOf
import com.moviemate.app.ui.theme.MovieMateTheme

/**
 * The app's front door: works out where this person belongs and goes there.
 *
 * A destination rather than logic in the Activity so that signing in lands here
 * too. "Where does a returning user go" is one question with one answer, and
 * having the launch path and the sign-in path each answer it separately is how
 * they drift apart.
 *
 * Renders the ground colour while it decides — a spinner for what is usually
 * one frame reads as a stutter.
 */
@Composable
fun RoutingScreen(onResolved: (String) -> Unit) {
    val graph = LocalAppGraph.current
    val viewModel: AppEntryViewModel = viewModel(
        factory = factoryOf { AppEntryViewModel(graph.sessionStore, graph.onboardingDraftStore) },
    )
    val entry by viewModel.entry.collectAsStateWithLifecycle()

    LaunchedEffect(entry) {
        (entry as? AppEntry.Route)?.let { onResolved(it.route) }
    }

    Box(Modifier.fillMaxSize().background(MovieMateTheme.colors.surfaceGround))
}
