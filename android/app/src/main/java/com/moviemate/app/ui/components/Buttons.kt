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
import com.moviemate.app.ui.theme.MovieMateColors
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Radius
import com.moviemate.app.ui.theme.Spacing

/**
 * Full-width pill CTA — Design System v8 §5.
 *
 * Label uses Inter, not the display face: legibility at button size is why
 * Anton was dropped from the type system.
 */
@Composable
fun PrimaryCta(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by rememberPressScale(interactionSource)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .background(
                color = when {
                    !enabled -> MovieMateColors.Blue.copy(alpha = 0.35f)
                    pressed -> MovieMateColors.BlueDark
                    else -> MovieMateColors.Blue
                },
                shape = RoundedCornerShape(Radius.pill),
            )
            .pressable(interactionSource = interactionSource, enabled = enabled, onClick = onClick)
            .padding(vertical = 17.dp, horizontal = Spacing.s5),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MovieMateType.cta, color = MovieMateColors.OnBlue)
    }
}

/** Quiet secondary action — used for "Not feeling it" on the match card. */
@Composable
fun SecondaryCta(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by rememberPressScale(interactionSource)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .background(
                color = if (pressed) MovieMateColors.Ink.copy(alpha = 0.06f) else Color.Transparent,
                shape = RoundedCornerShape(Radius.pill),
            )
            .pressable(interactionSource = interactionSource, onClick = onClick)
            .padding(vertical = 17.dp, horizontal = Spacing.s5),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MovieMateType.cta, color = MovieMateColors.InkSecondary)
    }
}
