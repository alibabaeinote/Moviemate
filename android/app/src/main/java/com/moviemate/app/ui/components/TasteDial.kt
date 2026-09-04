package com.moviemate.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.Opacity
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Radius
import com.moviemate.app.ui.theme.Space
import kotlin.math.roundToInt

/**
 * Taste Dial — the app's rating control.
 *
 * Continuous 0-100, never five discrete stars. Every taste profile in the
 * system reads this as a float; switching it to a 1-5 integer would invalidate
 * the whole recommendation engine.
 */
object TasteDial {
    const val MIN = 0f
    const val MAX = 100f

    private const val NOT_FOR_US = 20f
    private const val IT_WAS_FINE = 50f
    private const val REALLY_GOOD = 75f

    /** Dynamic label for a score. */
    fun labelFor(score: Float): String = when {
        score <= NOT_FOR_US -> "Not for us"
        score <= IT_WAS_FINE -> "It was fine"
        score <= REALLY_GOOD -> "Really good"
        else -> "Obsessed"
    }
}

@Composable
fun TasteDialSlider(
    score: Float,
    onScoreChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    filmTitle: String? = null,
) {
    val colors = MovieMateTheme.colors
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val fraction = ((score - TasteDial.MIN) / (TasteDial.MAX - TasteDial.MIN)).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        if (filmTitle != null) {
            Text(
                text = "Rate $filmTitle",
                style = MovieMateType.filmTitle,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(Space.stackTight))
        }

        Text(
            text = score.roundToInt().toString(),
            style = MovieMateType.statNumber,
            color = colors.textAccent,
        )
        Text(
            text = TasteDial.labelFor(score),
            style = MovieMateType.statCaption,
            color = colors.textPrimary,
        )

        Spacer(Modifier.height(Space.stack))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .background(colors.surfaceSunken)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        if (trackWidthPx > 0f) {
                            val ratio = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                            onScoreChange(ratio * TasteDial.MAX)
                        }
                    }
                }
                .semantics {
                    contentDescription = "Taste Dial, ${score.roundToInt()} out of 100"
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(score, TasteDial.MIN..TasteDial.MAX, 0)
                },
        ) {
            // fillMaxWidth rejects a fraction of 0, so skip the fill at zero.
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(16.dp)
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(
                            Brush.horizontalGradient(
                                listOf(colors.actionPrimaryFill, colors.actionRewardFill),
                            ),
                        ),
                )
            }
        }
    }
}

/**
 * Shared taste axis — the signature component.
 *
 * Both partners are pinned on ONE line and the band between them is the
 * agreement. Two separate numbers would show the same data and tell a different
 * story; mutual taste is the product, so it gets one axis.
 */
@Composable
fun SharedTasteAxis(
    scoreA: Float,
    scoreB: Float,
    modifier: Modifier = Modifier,
    trackHeight: androidx.compose.ui.unit.Dp = 8.dp,
) {
    val colors = MovieMateTheme.colors
    val low = (minOf(scoreA, scoreB) / TasteDial.MAX).coerceIn(0f, 1f)
    val high = (maxOf(scoreA, scoreB) / TasteDial.MAX).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(RoundedCornerShape(Radius.pill))
            .background(colors.surfaceSunken)
            .semantics {
                contentDescription =
                    "Shared taste: ${scoreA.roundToInt()} and ${scoreB.roundToInt()} out of 100"
            },
    ) {
        // Up to the higher score is where only one of them landed.
        if (high > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(high)
                    .height(trackHeight)
                    .background(colors.partnerA.copy(alpha = Opacity.scrimSoft)),
            )
        }
        // Up to the lower score is what they genuinely share.
        if (low > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(low)
                    .height(trackHeight)
                    .background(colors.partnerA),
            )
        }
    }
}
