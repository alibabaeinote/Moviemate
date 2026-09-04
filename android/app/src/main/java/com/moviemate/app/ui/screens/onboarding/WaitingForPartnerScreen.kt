package com.moviemate.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviemate.app.data.session.SessionStore
import com.moviemate.app.di.LocalAppGraph
import com.moviemate.app.ui.core.factoryOf
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Space
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * What the pair is still waiting on, in the order it resolves.
 *
 * Three distinct waits, not one: nobody has joined; they joined but are still
 * rating; both are done. Collapsing them into a single "waiting…" hides the one
 * thing the user can act on — if the partner never joined, the code may need
 * re-sending.
 */
enum class WaitingStage { NoPartner, PartnerRating, Ready }

class WaitingForPartnerViewModel(sessionStore: SessionStore) : ViewModel() {

    val stage: StateFlow<WaitingStage> = sessionStore.session
        .map { session ->
            when {
                session == null || !session.partnerJoined -> WaitingStage.NoPartner
                session.bothOnboarded -> WaitingStage.Ready
                else -> WaitingStage.PartnerRating
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WaitingStage.NoPartner)
}

/**
 * The holding screen between finishing your own onboarding and the first match.
 *
 * `aBothOnboarded` is set server-side by onRatingComplete, so this screen only
 * ever reads it. Two clients each deciding for themselves that the pair is
 * ready is the race that flag exists to prevent.
 */
@Composable
fun WaitingForPartnerScreen(onReady: () -> Unit) {
    val graph = LocalAppGraph.current
    val viewModel: WaitingForPartnerViewModel = viewModel(
        factory = factoryOf { WaitingForPartnerViewModel(graph.sessionStore) },
    )
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    val colors = MovieMateTheme.colors

    LaunchedEffect(stage) { if (stage == WaitingStage.Ready) onReady() }

    OnboardingScaffold {
        when (stage) {
            WaitingStage.NoPartner -> {
                Text(
                    "WAITING ON THEM",
                    style = MovieMateType.megaHeadline,
                    color = colors.textPrimary,
                )
                Text(
                    "Your ratings are saved. As soon as your partner enters the code and " +
                        "rates their own ten films, tonight's pick starts arriving.",
                    style = MovieMateType.body,
                    color = colors.textSecondary,
                )
            }

            WaitingStage.PartnerRating -> {
                Text(
                    "THEY'RE RATING",
                    style = MovieMateType.megaHeadline,
                    color = colors.textPrimary,
                )
                Text(
                    "Your partner is working through their ten films. " +
                        "We'll notify you both the moment the first match is ready.",
                    style = MovieMateType.body,
                    color = colors.textSecondary,
                )
            }

            // Held for the frame before onReady navigates away.
            WaitingStage.Ready -> {
                Text("YOU'RE SET", style = MovieMateType.megaHeadline, color = colors.textPrimary)
            }
        }

        Spacer(Modifier.height(Space.sectionGap))
    }
}
