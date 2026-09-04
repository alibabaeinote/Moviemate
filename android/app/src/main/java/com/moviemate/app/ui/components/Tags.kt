package com.moviemate.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Radius
import com.moviemate.app.ui.theme.Space

/**
 * Which semantic role a pill tag carries.
 *
 * Note there is no Coral tone: coral is decorative and never sits behind
 * interactive or informational text (design system §4.3). White on coral
 * measures 2.84:1.
 */
enum class TagTone { Accent, Reward }

/** Pill tag with a leading dot, sitting on the bottom edge of a poster. */
@Composable
fun PillTag(
    label: String,
    tone: TagTone,
    modifier: Modifier = Modifier,
) {
    val colors = MovieMateTheme.colors
    val background = if (tone == TagTone.Accent) colors.actionPrimaryFill else colors.actionRewardFill
    val content = if (tone == TagTone.Accent) colors.textOnFill else colors.textOnReward

    Row(
        modifier = modifier
            .background(background, RoundedCornerShape(Radius.pill))
            .padding(horizontal = Space.stackTight, vertical = Space.inline),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.inline),
    ) {
        Box(Modifier.size(6.dp).background(content.copy(alpha = 0.7f), CircleShape))
        Text(text = label, style = MovieMateType.tag, color = content)
    }
}

/** The tag pair that sits on the bottom edge of a hero poster. */
@Composable
fun HeroTagRow(
    accentLabel: String,
    rewardLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.inline),
    ) {
        PillTag(accentLabel, TagTone.Accent)
        PillTag(rewardLabel, TagTone.Reward)
    }
}

/** How far [HeroTagRow] rides up over the poster above it. */
val HERO_TAG_OVERLAP = 18.dp
