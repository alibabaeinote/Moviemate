package com.moviemate.app.nav

/**
 * Navigation routes.
 *
 * The `deepLinkTarget` values match the strings Cloud Functions put in the FCM
 * data payload (functions/src/notifications/types.ts). Keep the two in sync —
 * a mismatch silently drops the user on Home instead of the screen the
 * notification promised.
 */
object Routes {
    /** Decides where a launch or a sign-in actually lands. See RoutingScreen. */
    const val ROUTING = "routing"

    const val WELCOME = "welcome"
    const val SIGN_UP = "signUp"
    const val SIGN_IN = "signIn"
    const val FORGOT_PASSWORD = "forgotPassword"
    const val VERIFY_EMAIL = "verifyEmail"

    const val ONBOARDING_RATE = "onboarding/rate"
    const val INVITE_PARTNER = "onboarding/invite"
    const val JOIN_PARTNER = "onboarding/join"
    const val NOTIFICATION_PERMISSION = "onboarding/notifications"
    const val WAITING_FOR_PARTNER = "onboarding/waiting"

    const val MATCH = "match"
    const val WATCHLIST = "watchlist"
    const val US = "us"

    /**
     * Both carry the match they act on. Reading it from an argument rather than
     * from whatever the match listener happens to hold means a notification tap
     * cannot land the user on yesterday's film.
     */
    const val ARG_MATCH_ID = "matchId"
    const val REMINDER = "reminder/{$ARG_MATCH_ID}"
    const val RATE_WATCHED = "rate/watched/{$ARG_MATCH_ID}"

    const val SETTINGS = "us/settings"
    const val ABOUT = "us/about"

    fun reminder(matchId: String) = "reminder/$matchId"

    fun rateWatched(matchId: String) = "rate/watched/$matchId"
}

/**
 * Maps an FCM `deepLinkTarget` onto a route.
 *
 * `rate` and `reminder` both resolve to the Match tab rather than to the
 * argument-bearing screens: the payload carries no match id the client can
 * trust to still be current, and the Match tab reads the live match and shows
 * whichever phase it is actually in. Landing someone on a hard-coded rating
 * screen for a match that has since moved on is worse than landing them one tap
 * away from the right one.
 *
 * `rate` is `partner_rated` — "they've rated it, your turn" — not the
 * onboarding deck, which no notification ever links to.
 *
 * Values must stay in step with DeepLinkTarget in
 * functions/src/notifications/types.ts.
 */
fun routeForDeepLink(target: String?): String = when (target) {
    "match", "rate", "reminder" -> Routes.MATCH
    "watchlist" -> Routes.WATCHLIST
    "onboarding" -> Routes.WAITING_FOR_PARTNER
    else -> Routes.MATCH
}
