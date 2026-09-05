package com.moviemate.app.ui.screens.match

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviemate.app.di.LocalAppGraph
import com.moviemate.app.ui.components.PrimaryCta
import com.moviemate.app.ui.components.SecondaryCta
import com.moviemate.app.ui.core.ActionState
import com.moviemate.app.ui.core.factoryOf
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Space

/**
 * Agree a time, and let the server send the 15-minute reminder.
 *
 * Three suggested slots rather than a time picker: the pair has already made
 * the hard decision, and a wheel picker for "tonight at nine" is friction
 * charged for nothing. The slots roll to tomorrow once they have passed.
 */
@Composable
fun ScheduleWatchScreen(
    matchId: String,
    onDone: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val viewModel: MatchViewModel = viewModel(
        factory = factoryOf {
            MatchViewModel(graph.pairRepository, graph.filmRepository, graph.sessionStore)
        },
    )
    val action by viewModel.action.collectAsStateWithLifecycle()
    val colors = MovieMateTheme.colors

    // Computed once per visit: recomputing on recomposition would shuffle the
    // options under the user's finger as a slot crosses its hour.
    val slots = remember { suggestedWatchTimes() }

    LaunchedEffect(action) { if (action is ActionState.Succeeded) onDone() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceGround)
            .padding(horizontal = Space.screenGutter, vertical = Space.screenTop),
        verticalArrangement = Arrangement.spacedBy(Space.stack),
    ) {
        Text("WHEN?", style = MovieMateType.megaHeadline, color = colors.textPrimary)
        Text(
            "We'll nudge you both fifteen minutes before.",
            style = MovieMateType.body,
            color = colors.textSecondary,
        )

        Spacer(Modifier.height(Space.stackTight))

        slots.forEach { slot ->
            PrimaryCta(
                label = formatWatchTime(slot).replaceFirstChar { it.uppercase() },
                onClick = { viewModel.schedule(matchId, slot) },
                enabled = !action.isRunning,
            )
        }

        (action as? ActionState.Failed)?.let {
            Text(it.message, style = MovieMateType.meta, color = colors.statusDecorative)
        }

        SecondaryCta(label = "Not now", onClick = onDone)
    }
}
