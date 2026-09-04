package com.moviemate.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * TIER 2 — Semantic roles.
 *
 * Mirrors `sys.*` in design/tokens.json. **This is the only colour surface a
 * screen or component may read.**
 *
 * Every field is a role ("the colour of secondary text"), never a value ("grey").
 * That indirection is what lets the palette change without touching a single
 * screen — the whole point of the v8→v9 inversion landing in two files.
 *
 * ## Adding a colour
 * If a component needs a colour that has no field here, the answer is almost
 * always that a *role is missing*, not that a primitive should be used directly.
 * Add the field, define it in BOTH schemes below, mirror it into tokens.json, and
 * re-run the validator — it fails the build if the two themes drift apart or a
 * pairing drops below WCAG AA.
 *
 * Read via [MovieMateTheme.colors] rather than referencing the schemes directly.
 */
@Immutable
data class MovieMateColorScheme(
    // ---- Surfaces ----
    /** App background. */
    val surfaceGround: Color,
    /** Cards, sheets, list rows. */
    val surfaceRaised: Color,
    /** Track backgrounds and pressed insets. */
    val surfaceSunken: Color,
    /** Tinted container, e.g. the active nav pill. */
    val surfaceAccent: Color,
    /** An outstanding task the user still owes. */
    val surfaceWarning: Color,

    // ---- Borders ----
    val borderHairline: Color,
    val borderAccent: Color,
    val borderWarning: Color,

    // ---- Text ----
    val textPrimary: Color,
    val textSecondary: Color,
    /** Blue text and icons. On dark this is the LIFTED blue, not the fill blue. */
    val textAccent: Color,
    val textReward: Color,
    /** Text sitting on top of [actionPrimaryFill]. */
    val textOnFill: Color,
    /** Text sitting on top of [actionRewardFill]. */
    val textOnReward: Color,

    // ---- Actions ----
    val actionPrimaryFill: Color,
    val actionPrimaryHover: Color,
    val actionPrimaryPressed: Color,
    /**
     * Reserved for completion: mutual commitment reached, "We watched it", a best
     * week. Spending it on ordinary controls is what made lime read as decoration
     * in v8.
     */
    val actionRewardFill: Color,
    val actionRewardHover: Color,
    val actionQuietBorder: Color,
    val actionQuietText: Color,
    /** Focus ring. Lime on dark, because it must clear both the ground and a blue fill. */
    val actionFocusRing: Color,

    // ---- Status ----
    /**
     * Ornament and the "waiting on you" label. Never a fill behind interactive
     * text — see the design system doc §4.3 for why this rule exists.
     */
    val statusDecorative: Color,
    val statusCommitted: Color,
    val statusPending: Color,

    // ---- Partner identity ----
    /** Fixed per side so a colour always means the same person across screens. */
    val partnerA: Color,
    val partnerB: Color,

    /** True when this scheme is the dark one — for scrims and elevation choices. */
    val isDark: Boolean,
)

/**
 * Default theme. Film posters are composed for dark surrounds, so a light ground
 * fights every poster the app displays.
 */
internal val DarkColors = MovieMateColorScheme(
    surfaceGround = Ref.Ink0,
    surfaceRaised = Ref.Ink1,
    surfaceSunken = Ref.Ink2,
    surfaceAccent = Ref.BlueWashDark,
    surfaceWarning = Ref.AmberGround,

    borderHairline = Ref.Ink3,
    borderAccent = Ref.BlueHairDark,
    borderWarning = Ref.AmberHair,

    textPrimary = Ref.Paper50,
    textSecondary = Ref.Ink700,
    textAccent = Ref.Blue400,     // NOT Blue600 — that measures 2.35:1 here
    textReward = Ref.Lime500,
    textOnFill = Ref.Paper0,
    textOnReward = Ref.Ink900,

    actionPrimaryFill = Ref.Blue600,
    actionPrimaryHover = Ref.Blue500,
    actionPrimaryPressed = Ref.Blue700,
    actionRewardFill = Ref.Lime500,
    actionRewardHover = Ref.Lime400,
    actionQuietBorder = Ref.Ink3,
    actionQuietText = Ref.Ink700,
    actionFocusRing = Ref.Lime500,

    statusDecorative = Ref.Coral500,
    statusCommitted = Ref.Lime500,
    statusPending = Ref.Ink3,

    partnerA = Ref.Blue400,
    partnerB = Ref.Lime500,

    isDark = true,
)

/**
 * Maintained, not deprecated. The v8 identity still resolves cleanly — only the
 * role mapping differs.
 */
internal val LightColors = MovieMateColorScheme(
    surfaceGround = Ref.Paper50,
    surfaceRaised = Ref.Paper0,
    surfaceSunken = Ref.Paper100,
    surfaceAccent = Ref.BlueWashLight,
    surfaceWarning = Ref.Paper100,

    borderHairline = Ref.Paper200,
    borderAccent = Ref.BlueWashLight,
    borderWarning = Ref.Paper200,

    textPrimary = Ref.Ink900,
    textSecondary = Ref.Ink500,
    textAccent = Ref.Blue600,
    // Lime cannot carry text on paper, so the reward reads as ink and lime
    // becomes its fill instead.
    textReward = Ref.Ink900,
    textOnFill = Ref.Paper0,
    textOnReward = Ref.Ink900,

    actionPrimaryFill = Ref.Blue600,
    actionPrimaryHover = Ref.Blue500,
    actionPrimaryPressed = Ref.Blue700,
    actionRewardFill = Ref.Lime500,
    actionRewardHover = Ref.Lime400,
    actionQuietBorder = Ref.Paper200,
    actionQuietText = Ref.Ink500,
    actionFocusRing = Ref.Blue700,

    statusDecorative = Ref.Coral500,
    statusCommitted = Ref.Ink900,
    statusPending = Ref.Paper200,

    partnerA = Ref.Blue600,
    partnerB = Ref.Ink900,

    isDark = false,
)

/**
 * TIER 2 — Semantic spacing.
 *
 * Named by intent rather than size, so "the gap between stacked blocks" can be
 * retuned globally without hunting for every `16.dp`.
 */
object Space {
    /** Between two items on the same line, tight. */
    val inlineTight = Ref.D1
    /** Between two items on the same line. */
    val inline = Ref.D2
    /** Between stacked items inside one group. */
    val stackTight = Ref.D3
    /** Between stacked groups. */
    val stack = Ref.D4
    /** Left/right screen margin. */
    val screenGutter = Ref.D5
    /** Between major sections. */
    val sectionGap = Ref.D6
    /** Above the first element on a screen. */
    val screenTop = Ref.D7
}

/** TIER 2 — Corner radii, named by the shape they belong to. */
object Radius {
    /** Small inline chips. */
    val chip = Ref.RadiusChip
    /** Standard card. */
    val card = Ref.RadiusCard
    /** Large / hero card. */
    val hero = Ref.RadiusHero
    /** Buttons, tags, avatars, the nav bar. */
    val pill = Ref.RadiusPill
}

/**
 * TIER 2 — Motion.
 *
 * Every micro-interaction resolves under 300ms. Duration alone is not a motion
 * system; the curve is what makes it read as intentional rather than mechanical.
 * Nothing in the app uses linear easing.
 */
object Motion {
    /** Colour and opacity changes. */
    const val stateChangeMs = Ref.DurationInstant
    /** Button press scale. */
    const val pressMs = Ref.DurationQuick
    /** A card settling in; the checkmark drawing itself. */
    const val enterMs = Ref.DurationSettle

    const val pressScale = Ref.ScalePress

    /** Default for state changes. */
    val easeStandard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Elements entering the screen. */
    val easeEnter = CubicBezierEasing(0f, 0f, 0f, 1f)
    /** Elements leaving the screen. */
    val easeExit = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    /** The mutual-commitment confirmation, and nothing else. */
    val easeEmphasis = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/**
 * TIER 2 — Opacity, by intent.
 *
 * Components read these rather than [Ref] directly, same as every other tier-2
 * role — the tier rule has no exceptions.
 */
object Opacity {
    /** A control that cannot be pressed yet, e.g. the CTA while waiting on a partner. */
    const val disabled = Ref.OpacityDisabled
    /** Tint behind a pressed quiet button. */
    const val pressWash = Ref.OpacityPressWash
    /** Film grain overlay on dark surfaces. */
    const val grain = Ref.OpacityGrain
    /** The lighter band on the shared taste axis, where only one partner reached. */
    const val scrimSoft = 0.35f
}

/**
 * TIER 2 — Border widths.
 *
 * Thickness only; the colour comes from a [MovieMateColorScheme] border role.
 */
object BorderWidth {
    /** Between stat cells and list sections. */
    val divider = Ref.BorderHairline
    /** Card and nav-bar outline. */
    val container = Ref.BorderHairline
    /** Text field outline. */
    val input = Ref.BorderThin
    /** Avatar ring showing whether that partner has committed. */
    val commitRing = Ref.BorderThick
    /** Focus indicator. */
    val focus = Ref.BorderFocus
}

/**
 * TIER 2 — Elevation.
 *
 * Depth reads primarily through surface lightness on a dark UI; these shadow
 * values are a supporting cue, not the mechanism. Pair each level with the
 * surface role named in its doc.
 */
object Elevation {
    /** Flush with the ground. */
    val flat = Ref.Elevation0
    /** Cards and Watchlist rows — carried by `surfaceRaised`. */
    val card = Ref.Elevation1
    /** Bottom sheets and menus. */
    val sheet = Ref.Elevation2
    /** The floating bottom nav; dialogs. */
    val floating = Ref.Elevation3
}

/**
 * TIER 2 — Icon sizes.
 *
 * Line icons on a 24x24 grid, stroke 1.75-2dp, round caps and joins. Emoji is
 * never a UI icon — only an optional post-watch reaction.
 */
object IconSize {
    /** Sitting inside a meta line. */
    val inline = Ref.IconSm
    /** Bottom navigation. */
    val nav = Ref.IconMd
    /** Default, matching the design grid. */
    val default = Ref.IconLg
    /** Empty states. */
    val feature = Ref.IconXl

    val stroke = Ref.IconStroke
    val strokeThin = Ref.IconStrokeThin
}

/** TIER 2 — Focus. Every interactive element must show one. */
object Focus {
    val ringWidth = Ref.BorderFocus
    /** Gap between element and ring, so the ring reads as separate. */
    val ringOffset = Ref.D1
}

/**
 * TIER 2 — Density.
 *
 * Comfortable is the default. Compact is only for the long Watched list, where
 * scanning beats breathing room. [touchMin] is a floor regardless.
 */
object Density {
    val rowDefault = Ref.RowComfortable
    val rowCompact = Ref.RowCompact
    val touchMin = Ref.TouchMin
}

/**
 * TIER 2 — Window size classes and layout.
 *
 * v1 ships compact only. The other two are declared so the first foldable is a
 * layout change rather than a redesign.
 */
object Breakpoint {
    val compact = Ref.BreakpointCompact
    val medium = Ref.BreakpointMedium
    val expanded = Ref.BreakpointExpanded
}

object Layout {
    /** Left/right margin on every screen. */
    val screenPadding = Ref.D5

    /** Reading measure ceiling on medium+, so text never runs a tablet's full width. */
    val contentMaxWidth = 600.dp

    /** Match card. Portrait, because film posters are portrait. */
    const val POSTER_HERO_RATIO = 4f / 5f
    /** List thumbnail — the true poster ratio. */
    const val POSTER_THUMB_RATIO = 2f / 3f
    /** Landscape stills only. Never a poster. */
    const val STILL_RATIO = 16f / 10f

    fun columnsFor(width: androidx.compose.ui.unit.Dp): Int = when {
        width >= Breakpoint.expanded -> Ref.GridColumnsExpanded
        width >= Breakpoint.medium -> Ref.GridColumnsMedium
        else -> Ref.GridColumnsCompact
    }

    fun gutterFor(width: androidx.compose.ui.unit.Dp) = when {
        width >= Breakpoint.expanded -> Ref.GridGutterExpanded
        width >= Breakpoint.medium -> Ref.GridGutterMedium
        else -> Ref.GridGutterCompact
    }
}
