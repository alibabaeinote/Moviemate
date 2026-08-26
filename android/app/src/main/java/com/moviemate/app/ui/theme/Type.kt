package com.moviemate.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.moviemate.app.R

/**
 * Typography from Design System v8 §3 — locked.
 *
 * Two families, with a hard split:
 *  - Big Shoulders Display: large headlines, film titles, stat numbers. Nothing else.
 *  - Inter: body, meta, tag labels, button text, nav labels, forms.
 *
 * Button text is deliberately Inter, not the display face — legibility at
 * button size was the reason Anton was dropped.
 */

val BigShouldersDisplay = FontFamily(
    Font(R.font.big_shoulders_display_bold, FontWeight.Bold),
    Font(R.font.big_shoulders_display_extrabold, FontWeight.ExtraBold),
    Font(R.font.big_shoulders_display_black, FontWeight.Black),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

/**
 * The named styles from the design system's scale. Prefer these over ad-hoc
 * TextStyle values — a new style should be added here, not invented inline.
 */
object MovieMateType {
    /** Page headline / date. 54sp, Black, uppercase, tight leading. */
    val megaHeadline = TextStyle(
        fontFamily = BigShouldersDisplay,
        fontWeight = FontWeight.Black,
        fontSize = 54.sp,
        lineHeight = 51.84.sp, // 0.96 line-height
        letterSpacing = (-0.5).sp,
    )

    /** Film or list-item title. 34sp. */
    val filmTitle = TextStyle(
        fontFamily = BigShouldersDisplay,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 34.sp,
    )

    /** Stat number, e.g. "98%". Always Blue. */
    val statNumber = TextStyle(
        fontFamily = BigShouldersDisplay,
        fontWeight = FontWeight.Black,
        fontSize = 44.sp,
        lineHeight = 44.sp,
        color = MovieMateColors.Blue,
    )

    /** Stat caption, e.g. "shared taste". */
    val statCaption = TextStyle(
        fontFamily = BigShouldersDisplay,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 24.sp,
        color = MovieMateColors.Ink,
    )

    /** Body copy. */
    val body = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp,
        lineHeight = 1.55.em,
        color = MovieMateColors.Ink,
    )

    /** Meta line under a title. */
    val meta = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
        color = MovieMateColors.InkSecondary,
    )

    /** CTA button label — Inter, never the display face. */
    val cta = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 15.5.sp,
        textAlign = TextAlign.Center,
    )

    /** Pill tag label. */
    val tag = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 12.5.sp,
    )

    /** Bottom-nav label. */
    val navLabel = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
    )

    /** Form field label. */
    val fieldLabel = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = MovieMateColors.InkSecondary,
    )
}

/** Material3 mapping, so stock components inherit the right faces. */
internal val MovieMateTypography = Typography(
    displayLarge = MovieMateType.megaHeadline,
    headlineLarge = MovieMateType.filmTitle,
    headlineMedium = MovieMateType.statCaption,
    bodyLarge = MovieMateType.body,
    bodyMedium = MovieMateType.body,
    labelLarge = MovieMateType.cta,
    labelMedium = MovieMateType.tag,
    labelSmall = MovieMateType.navLabel,
)
