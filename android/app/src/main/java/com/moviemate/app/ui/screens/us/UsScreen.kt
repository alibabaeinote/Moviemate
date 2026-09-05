package com.moviemate.app.ui.screens.us

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviemate.app.data.remote.TmdbApi
import com.moviemate.app.di.LocalAppGraph
import com.moviemate.app.ui.components.SecondaryCta
import com.moviemate.app.ui.core.UiStateHost
import com.moviemate.app.ui.core.factoryOf
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Radius
import com.moviemate.app.ui.theme.Space

/**
 * The shared record, plus the handful of settings that belong to one person.
 *
 * Three numbers and no badges: heavy gamification here was explicitly rejected
 * (PRD §7.4), because a streak worth protecting is a reason to lie about having
 * watched something.
 */
@Composable
fun UsScreen(onSignedOut: () -> Unit) {
    val graph = LocalAppGraph.current
    val viewModel: UsViewModel = viewModel(
        factory = factoryOf {
            UsViewModel(graph.pairRepository, graph.authRepository, graph.sessionStore)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MovieMateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceGround)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screenGutter, vertical = Space.screenTop),
        verticalArrangement = Arrangement.spacedBy(Space.stack),
    ) {
        Text("US", style = MovieMateType.megaHeadline, color = colors.textPrimary)

        UiStateHost(state = state) { stats ->
            Column(verticalArrangement = Arrangement.spacedBy(Space.stack)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.stack)) {
                    Stat(
                        value = stats.matches.toString(),
                        caption = "Matches",
                        note = "both confirmed",
                        modifier = Modifier.weight(1f),
                    )
                    Stat(
                        value = stats.watched.toString(),
                        caption = "Watched",
                        note = "together",
                        modifier = Modifier.weight(1f),
                    )
                    Stat(
                        value = stats.streak.toString(),
                        caption = "Streak",
                        note = "watches in a row",
                        modifier = Modifier.weight(1f),
                    )
                }

                if (!stats.partnerJoined) {
                    Text(
                        "Your partner hasn't joined yet, so these will stay at zero.",
                        style = MovieMateType.body,
                        color = colors.textSecondary,
                    )
                }

                Spacer(Modifier.height(Space.stackTight))

                Text("NOTIFICATIONS", style = MovieMateType.overline, color = colors.textSecondary)

                SettingRow(
                    label = "Tonight's pick",
                    checked = stats.notificationSettings.dailyMatch,
                    onChange = {
                        viewModel.updateNotifications(
                            stats.notificationSettings.copy(dailyMatch = it),
                        )
                    },
                )
                SettingRow(
                    label = "Partner activity",
                    checked = stats.notificationSettings.partnerActivity,
                    onChange = {
                        viewModel.updateNotifications(
                            stats.notificationSettings.copy(partnerActivity = it),
                        )
                    },
                )
                SettingRow(
                    label = "Watch reminders",
                    checked = stats.notificationSettings.reminders,
                    onChange = {
                        viewModel.updateNotifications(
                            stats.notificationSettings.copy(reminders = it),
                        )
                    },
                )

                Spacer(Modifier.height(Space.stackTight))

                Text("ABOUT", style = MovieMateType.overline, color = colors.textSecondary)
                // Required by TMDB's terms of use, not decoration.
                Text(
                    text = TmdbApi.ATTRIBUTION,
                    style = MovieMateType.meta,
                    color = colors.textSecondary,
                )

                Spacer(Modifier.height(Space.stackTight))

                SecondaryCta(
                    label = "Sign out",
                    onClick = {
                        viewModel.signOut()
                        onSignedOut()
                    },
                )
            }
        }
    }
}

/** One headline number. [note] carries the definition, which is not decoration. */
@Composable
private fun Stat(
    value: String,
    caption: String,
    note: String,
    modifier: Modifier = Modifier,
) {
    val colors = MovieMateTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.card))
            .background(colors.surfaceRaised)
            .padding(Space.stackTight),
    ) {
        Text(value, style = MovieMateType.statNumber, color = colors.textAccent)
        Text(caption, style = MovieMateType.statCaption, color = colors.textPrimary)
        // "Matches" without "both confirmed" invites the reading that it counts
        // suggestions, which would make the number meaningless (PRD §9).
        Text(note, style = MovieMateType.meta, color = colors.textSecondary)
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MovieMateTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MovieMateType.body, color = colors.textPrimary)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.textOnFill,
                checkedTrackColor = colors.actionPrimaryFill,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.surfaceSunken,
            ),
        )
    }
}
