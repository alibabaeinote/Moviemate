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
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.MovieMateType
import kotlinx.coroutines.launch

/**
 * A person's picture, or their initial on a flat tint when they have none.
 *
 * [ringColor] is `colors.partnerA` / `colors.partnerB` at every call site —
 * one color is always one person, on every screen (Design System §4.5), and
 * the avatar ring is where that identity shows up outside the Taste Dial.
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

        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(BorderStroke(2.dp, ringColor), CircleShape)
                .background(colors.surfaceSunken),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = if (name != null) "$name's picture" else "Profile picture",
                    modifier = Modifier.size(size).clip(CircleShape),
                )
            } else {
                Text(
                    text = name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = MovieMateType.statCaption,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun PulseRing(size: Dp, ringColor: Color) {
    val transition = rememberInfiniteTransition(label = "avatarPulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "pulseScale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "pulseAlpha",
    )
    Box(
        Modifier
            .size(size)
            .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
            .border(BorderStroke(2.dp, ringColor), CircleShape),
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
            .size(size)
            .graphicsLayer(scaleX = scale.value, scaleY = scale.value, alpha = alpha.value)
            .background(glowColor, CircleShape),
    )
}
