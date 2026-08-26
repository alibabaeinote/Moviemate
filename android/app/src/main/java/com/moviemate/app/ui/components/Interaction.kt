package com.moviemate.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * The press micro-interaction from Design System v8 §7: scale(0.95-0.97) while
 * held, under 300ms. Every tappable surface uses this, so presses feel the same
 * across the app.
 */
const val PRESS_SCALE = 0.97f

/**
 * Clickable without Material's ripple — the design system's press feedback is
 * the scale, not an ink splash.
 */
fun Modifier.pressable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = this
    .indication(interactionSource, null)
    .clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )

/** Animated press scale bound to an interaction source. */
@Composable
fun rememberPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = PRESS_SCALE,
): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        label = "pressScale",
    )
}

/** Convenience for surfaces that only need the scale, with their own source. */
fun Modifier.pressableCard(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    pressable(interactionSource = interactionSource, onClick = onClick)
}
