package com.moviemate.app.ui.screens.us

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviemate.app.data.remote.TmdbApi
import com.moviemate.app.di.LocalAppGraph
import com.moviemate.app.ui.components.Avatar
import com.moviemate.app.ui.components.SecondaryCta
import com.moviemate.app.ui.components.pressableCard
import com.moviemate.app.ui.core.UiStateHost
import com.moviemate.app.ui.core.factoryOf
import com.moviemate.app.ui.theme.BorderWidth
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
fun UsScreen(onSignedOut: () -> Unit, onEditProfile: () -> Unit) {
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
        Text("US", style = MovieMateType.megaHeadline, color = colors.textAccent)

        UiStateHost(state = state) { stats ->
            Column(verticalArrangement = Arrangement.spacedBy(Space.stack)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.sectionGap),
                ) {
                    PersonRow(
                        name = stats.myName,
                        avatarUrl = stats.myAvatarUrl,
                        ringColor = if (stats.isUserA) colors.partnerA else colors.partnerB,
                        modifier = Modifier.weight(1f).pressableCard(onEditProfile),
                    )
                    if (stats.partnerJoined) {
                        PersonRow(
                            name = stats.partnerName ?: "Your partner",
                            avatarUrl = stats.partnerAvatarUrl,
                            ringColor = if (stats.isUserA) colors.partnerB else colors.partnerA,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Row(
                    modifier = Modifier.height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(Space.stack),
                ) {
                    Stat(
                        value = stats.matches.toString(),
                        caption = "Matches",
                        note = "confirmed",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    Stat(
                        value = stats.watched.toString(),
                        caption = "Watched",
                        note = "together",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    Stat(
                        value = stats.streak.toString(),
                        caption = "Streak",
                        note = "in a row",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }

                if (!stats.partnerJoined) {
                    Text(
                        "Your partner hasn't joined yet, so these will stay at zero.",
                        style = MovieMateType.body,
                        color = colors.textSecondary,
                    )
                }

                if (stats.partnerJoined) {
                    Spacer(Modifier.height(Space.stackTight))
                    Text("YOUR JOURNEY", style = MovieMateType.overline, color = colors.textSecondary)
                    JourneyChart(weeks = stats.journeyWeeks)

                    Spacer(Modifier.height(Space.stackTight))
                    Text("TASTE COMPATIBILITY", style = MovieMateType.overline, color = colors.textSecondary)
                    CompatibilityRow(percent = stats.compatibilityPercent)
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

/**
 * One person's identity: picture, name, ring in their fixed color.
 *
 * Only the caller's own row is tappable (into [ProfileEditScreen]) — a
 * partner's picture and name are read-only here, same as everywhere else in
 * the app.
 */
@Composable
private fun PersonRow(
    name: String,
    avatarUrl: String?,
    ringColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = MovieMateTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.stackTight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name = name, avatarUrl = avatarUrl, ringColor = ringColor)
        Text(
            text = name,
            style = MovieMateType.listTitle,
            color = colors.textPrimary,
        )
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
            .border(BorderWidth.container, colors.borderHairline, RoundedCornerShape(Radius.card))
            .padding(Space.stack),
        verticalArrangement = Arrangement.spacedBy(Space.inlineTight),
    ) {
        Text(value, style = MovieMateType.statTileNumber, color = colors.textAccent)
        Text(caption, style = MovieMateType.statTileCaption, color = colors.textPrimary)
        // "Matches" without "confirmed" invites the reading that it counts
        // suggestions, which would make the number meaningless (PRD §9).
        Text(note, style = MovieMateType.meta, color = colors.textSecondary)
    }
}

/**
 * Six trailing weeks of watch counts, oldest to newest, as a row of progress
 * rings rather than a badge — a trend, not a score to protect (PRD §7.4).
 */
@Composable
private fun JourneyChart(weeks: List<Int>, modifier: Modifier = Modifier) {
    val colors = MovieMateTheme.colors
    val maxCount = (weeks.maxOrNull() ?: 0).coerceAtLeast(1)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(colors.surfaceRaised)
            .border(BorderWidth.container, colors.borderHairline, RoundedCornerShape(Radius.card))
            .padding(Space.stack),
        horizontalArrangement = Arrangement.spacedBy(Space.inlineTight),
    ) {
        weeks.forEach { count ->
            val fraction = count / maxCount.toFloat()
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.inlineTight),
            ) {
                Canvas(modifier = Modifier.size(JOURNEY_RING_SIZE)) {
                    val stroke = Stroke(width = JOURNEY_RING_STROKE.toPx(), cap = StrokeCap.Round)
                    drawArc(
                        color = colors.surfaceSunken,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = stroke,
                    )
                    drawArc(
                        color = colors.actionPrimaryFill,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        style = stroke,
                    )
                }
                Text(count.toString(), style = MovieMateType.meta, color = colors.textSecondary)
            }
        }
    }
}

private val JOURNEY_RING_SIZE = 44.dp
private val JOURNEY_RING_STROKE = 5.dp

/**
 * One line, not a badge tile: below [com.moviemate.app.ui.screens.us.UsStatsMath.MIN_SHARED_RATED_FILMS]
 * shared ratings the number would be noise, so it says so instead of guessing.
 */
@Composable
private fun CompatibilityRow(percent: Int?, modifier: Modifier = Modifier) {
    val colors = MovieMateTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(colors.surfaceRaised)
            .border(BorderWidth.container, colors.borderHairline, RoundedCornerShape(Radius.card))
            .padding(Space.stack),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (percent != null) {
                "You two agree on taste"
            } else {
                "Rate a few more films together to see this"
            },
            style = MovieMateType.body,
            color = colors.textPrimary,
        )
        if (percent != null) {
            Text("$percent%", style = MovieMateType.statTileNumber, color = colors.textAccent)
        }
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
