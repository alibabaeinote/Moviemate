package com.moviemate.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Radius
import com.moviemate.app.ui.theme.Opacity
import com.moviemate.app.ui.theme.Space

/**
 * Full-width pill CTA.
 *
 * [tone] picks a semantic role rather than a colour, so "the reward button" stays
 * the reward button when the palette moves.
 */
enum class CtaTone {
    /** Ordinary primary action. */
    Primary,

    /**
     * Completion only: mutual commitment reached, "We watched it", a best week.
     * Using this for an ordinary action is what turned lime into decoration.
     */
    Reward,
}

@Composable
fun PrimaryCta(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: CtaTone = CtaTone.Primary,
) {
    val colors = MovieMateTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by rememberPressScale(interactionSource)

    val fill = when {
        tone == CtaTone.Reward && pressed -> colors.actionRewardHover
        tone == CtaTone.Reward -> colors.actionRewardFill
        pressed -> colors.actionPrimaryPressed
        else -> colors.actionPrimaryFill
    }
    val content = if (tone == CtaTone.Reward) colors.textOnReward else colors.textOnFill

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .background(
                color = if (enabled) fill else fill.copy(alpha = Opacity.disabled),
                shape = RoundedCornerShape(Radius.pill),
            )
            .pressable(interactionSource = interactionSource, enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp, horizontal = Space.screenGutter),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MovieMateType.cta, color = content)
    }
}

/** Quiet secondary action — used for "Not feeling it" and similar. */
@Composable
fun SecondaryCta(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MovieMateTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by rememberPressScale(interactionSource)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .background(
                color = if (pressed) {
                    colors.textPrimary.copy(alpha = Opacity.pressWash)
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(Radius.pill),
            )
            .pressable(interactionSource = interactionSource, onClick = onClick)
            .padding(vertical = 15.dp, horizontal = Space.screenGutter),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MovieMateType.cta, color = colors.actionQuietText)
    }
}
