package com.moviemate.app.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviemate.app.di.LocalAppGraph
import com.moviemate.app.ui.components.PrimaryCta
import com.moviemate.app.ui.components.SecondaryCta
import com.moviemate.app.ui.core.ActionState
import com.moviemate.app.ui.core.UiStateHost
import com.moviemate.app.ui.core.factoryOf
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Radius
import com.moviemate.app.ui.theme.Space

@Composable
private fun pairSetupViewModel(): PairSetupViewModel {
    val graph = LocalAppGraph.current
    return viewModel(
        factory = factoryOf {
            PairSetupViewModel(
                graph.pairRepository,
                graph.sessionStore,
                graph.onboardingDraftStore,
            )
        },
    )
}

/**
 * Show the invite code and hand it over.
 *
 * The code is created the moment this screen opens, not when the user taps
 * share: there is nothing to show otherwise, and someone who reads the code out
 * loud over the phone never taps share at all.
 */
@Composable
fun InvitePartnerScreen(
    onContinue: () -> Unit,
    onJoinInstead: () -> Unit,
) {
    val viewModel = pairSetupViewModel()
    val state by viewModel.invite.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = MovieMateTheme.colors

    LaunchedEffect(Unit) { viewModel.startInvite() }

    OnboardingScaffold {
        Text("INVITE YOUR PARTNER", style = MovieMateType.megaHeadline, color = colors.textPrimary)
        Text(
            "They install MovieMate, enter this code, and rate their own ten films. " +
                "Then you both start getting one pick a night.",
            style = MovieMateType.body,
            color = colors.textSecondary,
        )

        Spacer(Modifier.height(Space.stackTight))

        UiStateHost(state = state, onRetry = viewModel::startInvite) { invite ->
            Column(verticalArrangement = Arrangement.spacedBy(Space.stack)) {
                CodeBlock(invite.inviteCode)

                PrimaryCta(
                    label = "Share the code",
                    onClick = { context.shareInviteCode(invite.inviteCode) },
                )
                PrimaryCta(
                    label = if (ready) "Done — next" else "Saving your ratings…",
                    onClick = onContinue,
                    enabled = ready,
                )
            }
        }

        SecondaryCta(label = "I have a code instead", onClick = onJoinInstead)
    }
}

/** The code, set large enough to read aloud from across a room. */
@Composable
private fun CodeBlock(code: String) {
    val colors = MovieMateTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised, RoundedCornerShape(Radius.card))
            .padding(vertical = Space.sectionGap, horizontal = Space.stack),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = code,
            style = MovieMateType.statNumber,
            color = colors.textAccent,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Enter a partner's code.
 *
 * Errors here are the server's words — "This invite has already been used",
 * "That's your own invite code" — because they say precisely what went wrong
 * and a generic "Couldn't join" does not.
 */
@Composable
fun JoinPartnerScreen(
    onJoined: () -> Unit,
    onInviteInstead: () -> Unit,
) {
    val viewModel = pairSetupViewModel()
    val action by viewModel.action.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val colors = MovieMateTheme.colors
    var code by remember { mutableStateOf("") }

    LaunchedEffect(ready) { if (ready) onJoined() }

    OnboardingScaffold {
        Text("JOIN YOUR PARTNER", style = MovieMateType.megaHeadline, color = colors.textPrimary)
        Text(
            "Enter the code they sent you.",
            style = MovieMateType.body,
            color = colors.textSecondary,
        )

        Spacer(Modifier.height(Space.stackTight))

        OutlinedTextField(
            value = code,
            onValueChange = {
                code = it.uppercase()
                viewModel.dismissActionError()
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.chip),
            textStyle = MovieMateType.filmTitle,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.textAccent,
                unfocusedBorderColor = colors.borderHairline,
                focusedContainerColor = colors.surfaceRaised,
                unfocusedContainerColor = colors.surfaceRaised,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                cursorColor = colors.textAccent,
            ),
        )

        (action as? ActionState.Failed)?.let {
            Text(it.message, style = MovieMateType.meta, color = colors.statusDecorative)
        }

        PrimaryCta(
            label = if (action.isRunning) "Joining…" else "Join",
            onClick = { viewModel.join(code) },
            enabled = code.isNotBlank() && !action.isRunning,
        )
        SecondaryCta(label = "I want to invite instead", onClick = onInviteInstead)
    }
}
