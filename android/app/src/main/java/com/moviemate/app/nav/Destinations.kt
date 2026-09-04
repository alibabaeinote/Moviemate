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
    const val REMINDER = "reminder"
    const val RATE_WATCHED = "rate/watched"
    const val SETTINGS = "us/settings"
    const val ABOUT = "us/about"
}

/** Maps an FCM `deepLinkTarget` onto a route. Unknown values fall back to Match. */
fun routeForDeepLink(target: String?): String = when (target) {
    "match" -> Routes.MATCH
    "watchlist" -> Routes.WATCHLIST
    "rate" -> Routes.ONBOARDING_RATE
    "reminder" -> Routes.REMINDER
    "onboarding" -> Routes.WAITING_FOR_PARTNER
    else -> Routes.MATCH
}
