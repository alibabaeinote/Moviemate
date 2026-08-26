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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moviemate.app.ui.theme.MovieMateColors
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Radius
import com.moviemate.app.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * Taste Dial — the app's rating control (PRD §7.3).
 *
 * Continuous 0-100, never five discrete stars. The whole recommendation engine
 * reads this number as a float; changing it to a 1-5 integer would invalidate
 * every taste profile.
 *
 * The fill gradients from Blue to Lime as the score rises (Design System §7).
 */
object TasteDial {
    const val MIN = 0f
    const val MAX = 100f

    /** Dynamic labels from PRD §7.3. */
    fun labelFor(score: Float): String = when {
        score <= 20f -> "Not for us"
        score <= 50f -> "It was fine"
        score <= 75f -> "Really good"
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
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val fraction = (score - TasteDial.MIN) / (TasteDial.MAX - TasteDial.MIN)

    Column(modifier = modifier.fillMaxWidth()) {
        if (filmTitle != null) {
            Text(text = "Rate $filmTitle", style = MovieMateType.filmTitle)
            Spacer(Modifier.height(Spacing.s3))
        }

        Text(text = score.roundToInt().toString(), style = MovieMateType.statNumber)
        Text(text = TasteDial.labelFor(score), style = MovieMateType.statCaption)

        Spacer(Modifier.height(Spacing.s4))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .background(MovieMateColors.Ink.copy(alpha = 0.08f))
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
            // fillMaxWidth rejects a fraction of 0, so skip the fill entirely at zero.
            val fill = fraction.coerceIn(0f, 1f)
            if (fill > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fill)
                        .height(18.dp)
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(
                            Brush.horizontalGradient(
                                listOf(MovieMateColors.Blue, MovieMateColors.Lime),
                            ),
                        ),
                )
            }
        }
    }
}

/**
 * Both partners' scores on one shared bar — reinforces "shared taste" rather
 * than two separate numbers (PRD §7.4 item 6).
 */
@Composable
fun SharedTasteBar(
    scoreA: Float,
    scoreB: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(Radius.pill))
            .background(MovieMateColors.Ink.copy(alpha = 0.08f))
            .semantics {
                contentDescription =
                    "Shared taste: ${scoreA.roundToInt()} and ${scoreB.roundToInt()} out of 100"
            },
    ) {
        val low = (minOf(scoreA, scoreB) / TasteDial.MAX).coerceIn(0f, 1f)
        val high = (maxOf(scoreA, scoreB) / TasteDial.MAX).coerceIn(0f, 1f)

        // The band up to the higher score is where only one of them landed; the
        // band up to the lower score is what they genuinely share.
        if (high > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(high)
                    .height(10.dp)
                    .background(MovieMateColors.Blue.copy(alpha = 0.35f)),
            )
        }
        if (low > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(low)
                    .height(10.dp)
                    .background(MovieMateColors.Blue),
            )
        }
    }
}
