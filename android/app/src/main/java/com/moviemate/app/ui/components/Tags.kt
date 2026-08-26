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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moviemate.app.ui.theme.MovieMateColors
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Radius
import com.moviemate.app.ui.theme.Spacing

/** Which of the two allowed tag colours a pill uses (Design System §5). */
enum class TagTone { Blue, Lime }

/**
 * Pill tag with a leading dot — the pair that overlaps the bottom edge of a
 * hero image. Only two tones exist; Coral is decorative and never appears here
 * because tags sit on interactive cards.
 */
@Composable
fun PillTag(
    label: String,
    tone: TagTone,
    modifier: Modifier = Modifier,
) {
    val background = if (tone == TagTone.Blue) MovieMateColors.Blue else MovieMateColors.Lime
    val content = if (tone == TagTone.Blue) MovieMateColors.OnBlue else MovieMateColors.OnLime
    val dot = if (tone == TagTone.Blue) Color(0xFF5A66EC) else MovieMateColors.Ink

    Row(
        modifier = modifier
            .background(background, RoundedCornerShape(Radius.pill))
            .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Text(text = label, style = MovieMateType.tag, color = content)
    }
}

/**
 * The tag pair that sits on the bottom edge of a hero image.
 *
 * Apply [HERO_TAG_OVERLAP] as a negative top offset at the call site so the row
 * overlaps the image above it — that overlap is part of the locked spec
 * (Design System §5), not a layout accident.
 */
@Composable
fun HeroTagRow(
    blueLabel: String,
    limeLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        PillTag(blueLabel, TagTone.Blue)
        PillTag(limeLabel, TagTone.Lime)
    }
}

/** How far HeroTagRow rides up over the image above it. */
val HERO_TAG_OVERLAP = 18.dp
