package com.moviemate.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.SpaceGrotesk
import kotlinx.coroutines.launch

/**
 * A person's picture in a ring, or — with none — their initial on a flat
 * fill of their own identity color, not a ring around empty space.
 *
 * [ringColor] is `colors.partnerA` / `colors.partnerB` at every call site —
 * one color is always one person, on every screen (Design System §4.5).
 * With a picture it stays a ring around it; without one it becomes the fill
 * itself, since a ring around a blank tile reads as an empty state rather
 * than an identity.
 */
@Composable
fun Avatar(
    name: String?,
    avatarUrl: String?,
    ringColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    pulsing: Boolean = false,
    celebrating: Boolean = false,
) {
    val colors = MovieMateTheme.colors

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        // A "?" that hasn't resolved yet breathes rather than sitting dead still —
        // the one signal on this screen that the app is waiting, not stuck.
        if (pulsing) {
            PulseRing(size = size, ringColor = ringColor)
        }
        // The instant the wait ends, one deliberate pop — the only celebratory
        // motion in the whole app, spent on the single moment that earns it.
        if (celebrating) {
            JoinGlow(size = size, glowColor = colors.textReward)
        }

        if (avatarUrl != null) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(BorderStroke(2.dp, ringColor), CircleShape)
                    .background(colors.surfaceSunken),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = if (name != null) "$name's picture" else "Profile picture",
                    modifier = Modifier.size(size).clip(CircleShape),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(ringColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = initialStyle(size),
                    color = ringColor.legibleForeground(),
                )
            }
        }
    }
}

/**
 * Black or white, whichever reads on this fill. Needed because partner
 * colors aren't drawn from a fixed light/dark pair — dark theme keeps
 * `partner.b` lime as a deliberate identity exception (Design System §4.4),
 * which is far too bright for white text.
 */
private fun Color.legibleForeground(): Color =
    if (luminance() > 0.5f) Color.Black else Color.White

/**
 * Scaled from this call site's own [size], not a fixed [MovieMateType] role —
 * unlike a screen's type scale, Avatar renders at three different sizes
 * (44dp default, 64dp in the waiting screen, 96dp in profile edit), and a
 * single fixed glyph size reads as oversized at the small end and lost at
 * the large end.
 */
private fun initialStyle(size: Dp) = TextStyle(
    fontFamily = SpaceGrotesk,
    fontWeight = FontWeight.Bold,
    fontSize = (size.value * INITIAL_SIZE_RATIO).sp,
)

private const val INITIAL_SIZE_RATIO = 0.4f

@Composable
private fun PulseRing(size: Dp, ringColor: Color) {
    val transition = rememberInfiniteTransition(label = "avatarPulse")
    val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "pulseFraction",
    )
    Box(
        Modifier
            .size(size * (1f + 0.4f * fraction))
            .border(BorderStroke(2.dp, ringColor.copy(alpha = 0.4f * (1f - fraction))), CircleShape),
    )
}

@Composable
private fun JoinGlow(size: Dp, glowColor: Color) {
    val scale = remember { Animatable(0.55f) }
    val alpha = remember { Animatable(0.65f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1.5f, tween(900, easing = FastOutSlowInEasing)) }
        alpha.animateTo(0f, tween(900, easing = LinearEasing))
    }
    Box(
        Modifier
            .size(size * scale.value)
            .background(glowColor.copy(alpha = alpha.value), CircleShape),
    )
}
