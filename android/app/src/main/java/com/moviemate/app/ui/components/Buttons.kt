package com.moviemate.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.moviemate.app.ui.theme.BorderWidth
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Radius
import com.moviemate.app.ui.theme.Opacity
import com.moviemate.app.ui.theme.Space

/**
 * Full-width CTA.
 *
 * v10 moved off the full pill shape to [Radius.card] — a soft, moderate curve
 * rather than a stadium end. The bottom nav keeps the pill; buttons no longer
 * do (Design System §3, radius scale).
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
                shape = RoundedCornerShape(Radius.card),
            )
            .pressable(interactionSource = interactionSource, enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp, horizontal = Space.screenGutter),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MovieMateType.cta, color = content)
    }
}

/**
 * Quiet secondary action — used for "Not feeling it" and similar.
 *
 * Outlined, per Design System's Actions table (`colors.actionQuietBorder`):
 * "quiet" means lower-emphasis than a filled CTA, not invisible — a bare label
 * floating on the background gives no resting-state signal that it is tappable
 * at all.
 */
@Composable
fun SecondaryCta(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MovieMateTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by rememberPressScale(interactionSource)
    val contentAlpha = if (enabled) 1f else Opacity.disabled

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
                shape = RoundedCornerShape(Radius.card),
            )
            .border(
                BorderStroke(BorderWidth.container, colors.actionQuietBorder.copy(alpha = contentAlpha)),
                RoundedCornerShape(Radius.card),
            )
            .pressable(interactionSource = interactionSource, enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp, horizontal = Space.screenGutter),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MovieMateType.cta,
            color = colors.actionQuietText.copy(alpha = contentAlpha),
        )
    }
}

/**
 * A same-weight alternative path, not a decision — no container at all. The
 * coloured label is the whole affordance, like a link.
 *
 * Reserved for "instead" navigation ("I have a code instead", "I want to
 * invite instead") — two ways into the same flow, neither more committed
 * than the other. An action with a real consequence stays [SecondaryCta]:
 * without a container it reads as a stray line of text, not something that
 * was worth a deliberate tap.
 */
@Composable
fun LinkCta(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MovieMateTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interactionSource)
    val contentAlpha = if (enabled) 1f else Opacity.disabled

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .pressable(interactionSource = interactionSource, enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp, horizontal = Space.screenGutter),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MovieMateType.cta,
            color = colors.textAccent.copy(alpha = contentAlpha),
        )
    }
}
