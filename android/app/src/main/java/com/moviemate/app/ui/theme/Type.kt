package com.moviemate.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.moviemate.app.R

/**
 * TIER 2 — Typography roles.
 *
 * Mirrors `sys.type.*` in design/tokens.json. Roles are named by what they are
 * ("filmTitle"), never by size — a screen never asks for 34sp.
 *
 * **These styles carry no colour.** Colour is a separate role, applied at the
 * call site from [MovieMateTheme.colors]. Baking a colour into a type style is
 * what forces a second set of styles the moment a second theme exists.
 *
 * Two families, with a hard split:
 *  - Space Grotesk: large headlines, film titles, stat numbers. Nothing else.
 *  - Plus Jakarta Sans: body, meta, tag labels, button text, nav labels, forms.
 *
 * v10 replaced both faces together (was Big Shoulders Display + Inter): once
 * the palette dropped to a single accent, weight alone stopped being enough
 * contrast between headline and body, so the pairing now comes from two
 * genuinely distinct families rather than two weights of a related one.
 * Button text is still deliberately the body face, never the display one.
 */

/**
 * Both families ship as single variable fonts rather than one file per weight.
 *
 * Google Fonts now publishes only the variable cuts, and minSdk 26 supports
 * them, so this is two files instead of eight — and any weight in the range is
 * reachable, not just the ones we happened to download.
 *
 * A variable font ignores [FontWeight] on its own: the weight has to be passed
 * as a variation axis as well, which is what [FontVariation.Settings] does here.
 * Setting only the FontWeight would render every style at the default instance.
 */
@OptIn(ExperimentalTextApi::class)
private fun variableWeight(weight: FontWeight) =
    FontVariation.Settings(FontVariation.weight(weight.weight))

/** Space Grotesk's variable axis tops out at 700 — no headline role asks for more. */
@OptIn(ExperimentalTextApi::class)
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_variable, FontWeight.Bold, variationSettings = variableWeight(FontWeight.Bold)),
)

@OptIn(ExperimentalTextApi::class)
val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_variable, FontWeight.Normal, variationSettings = variableWeight(FontWeight.Normal)),
    Font(R.font.plus_jakarta_sans_variable, FontWeight.Medium, variationSettings = variableWeight(FontWeight.Medium)),
    Font(R.font.plus_jakarta_sans_variable, FontWeight.SemiBold, variationSettings = variableWeight(FontWeight.SemiBold)),
    Font(R.font.plus_jakarta_sans_variable, FontWeight.Bold, variationSettings = variableWeight(FontWeight.Bold)),
    Font(R.font.plus_jakarta_sans_variable, FontWeight.ExtraBold, variationSettings = variableWeight(FontWeight.ExtraBold)),
)

object MovieMateType {

    /** Page headline or date. Uppercase, tight leading. Pair with `colors.textAccent`. */
    val megaHeadline = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 54.sp,
        lineHeight = 51.84.sp, // 0.96
        letterSpacing = (-0.5).sp,
    )

    /** Film or list-item title. */
    val filmTitle = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 34.sp,
    )

    /** Stat number, e.g. "98". Pair with `colors.textAccent`. */
    val statNumber = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 39.6.sp, // 0.9
    )

    /** Stat caption, e.g. "shared taste". */
    val statCaption = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 24.sp,
    )

    /** Body copy. */
    val body = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp,
        lineHeight = 1.55.em,
    )

    /** Meta line under a title. Pair with `colors.textSecondary`. */
    val meta = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
    )

    /** CTA button label — body face, never the display face. */
    val cta = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 15.5.sp,
        textAlign = TextAlign.Center,
    )

    /** Pill tag label. */
    val tag = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 12.5.sp,
    )

    /** Bottom-nav label. */
    val navLabel = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
    )

    /** Small uppercase section label. Pair with `colors.textSecondary`. */
    val overline = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 10.5.sp,
        letterSpacing = 0.12.em,
    )

    /** Form field label. */
    val fieldLabel = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
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
