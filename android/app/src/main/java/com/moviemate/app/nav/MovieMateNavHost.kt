package com.moviemate.app.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.moviemate.app.ui.components.BottomNavItem
import com.moviemate.app.ui.components.MovieMateBottomNav
import com.moviemate.app.ui.screens.PlaceholderScreen
import com.moviemate.app.ui.screens.auth.ForgotPasswordScreen
import com.moviemate.app.ui.screens.auth.SignInScreen
import com.moviemate.app.ui.screens.auth.SignUpScreen
import com.moviemate.app.ui.screens.auth.WelcomeScreen
import com.moviemate.app.ui.screens.onboarding.InvitePartnerScreen
import com.moviemate.app.ui.screens.onboarding.JoinPartnerScreen
import com.moviemate.app.ui.screens.onboarding.NotificationPermissionScreen
import com.moviemate.app.ui.screens.onboarding.OnboardingRateScreen
import com.moviemate.app.ui.screens.match.MatchScreen
import com.moviemate.app.ui.screens.match.RateWatchedScreen
import com.moviemate.app.ui.screens.match.ScheduleWatchScreen
import com.moviemate.app.ui.screens.onboarding.WaitingForPartnerScreen
import com.moviemate.app.ui.theme.Space

/**
 * The three main tabs. Icons are placeholders from the Material set; the design
 * system calls for a custom 24x24 stroke set (§6), which is a design asset this
 * scaffold does not ship.
 */
private val bottomNavItems = listOf(
    BottomNavItem("Match", Icons.Outlined.DateRange, Routes.MATCH),
    BottomNavItem("Watchlist", Icons.AutoMirrored.Outlined.List, Routes.WATCHLIST),
    BottomNavItem("Us", Icons.Outlined.Person, Routes.US),
)

private val tabRoutes = bottomNavItems.map { it.route }.toSet()

@Composable
fun MovieMateNavHost(
    navController: NavHostController,
    pendingDeepLinkRoute: String?,
    onDeepLinkConsumed: () -> Unit,
    startDestination: String = Routes.ROUTING,
) {
    // Act on a notification tap once the graph is composed, from either entry path.
    LaunchedEffect(pendingDeepLinkRoute) {
        val route = pendingDeepLinkRoute ?: return@LaunchedEffect
        navController.navigate(route) { launchSingleTop = true }
        onDeepLinkConsumed()
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomNav = currentRoute in tabRoutes

    Scaffold(
        bottomBar = {
            if (showBottomNav && currentRoute != null) {
                Box(Modifier.padding(horizontal = Space.stack, vertical = Space.stackTight)) {
                    MovieMateBottomNav(
                        items = bottomNavItems,
                        selectedRoute = currentRoute,
                        onSelect = { route ->
                            navController.navigate(route) {
                                popUpTo(Routes.MATCH) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable(Routes.ROUTING) {
                RoutingScreen(onResolved = { navController.replaceWith(it) })
            }
            authGraph(navController)
            onboardingGraph(navController)
            mainGraph(navController)
        }
    }
}

private fun androidx.navigation.NavGraphBuilder.authGraph(navController: NavHostController) {
    composable(Routes.WELCOME) {
        WelcomeScreen(
            onSignUp = { navController.navigate(Routes.SIGN_UP) },
            onSignIn = { navController.navigate(Routes.SIGN_IN) },
        )
    }
    composable(Routes.SIGN_UP) {
        SignUpScreen(
            onSignedUp = { navController.replaceWith(Routes.ONBOARDING_RATE) },
            onSignInInstead = { navController.navigate(Routes.SIGN_IN) },
        )
    }
    composable(Routes.SIGN_IN) {
        SignInScreen(
            // Back through Routing, not straight to Match: a returning user may
            // be mid-onboarding, or waiting on a partner who never joined.
            onSignedIn = { navController.replaceWith(Routes.ROUTING) },
            onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
            onSignUpInstead = { navController.navigate(Routes.SIGN_UP) },
        )
    }
    composable(Routes.FORGOT_PASSWORD) {
        ForgotPasswordScreen(onDone = { navController.popBackStack() })
    }
}

/**
 * Onboarding is a chain, and every step pops the one before it.
 *
 * Back-navigating into a finished step is not a recoverable state here: the
 * rating deck is spent, and the invite screen would try to create a second pair
 * for someone who already has one. So each step replaces its predecessor rather
 * than stacking on it.
 */
private fun androidx.navigation.NavGraphBuilder.onboardingGraph(navController: NavHostController) {
    composable(Routes.ONBOARDING_RATE) {
        OnboardingRateScreen(
            onFinished = { navController.replaceWith(Routes.INVITE_PARTNER) },
        )
    }
    composable(Routes.INVITE_PARTNER) {
        InvitePartnerScreen(
            onContinue = { navController.replaceWith(Routes.NOTIFICATION_PERMISSION) },
            onJoinInstead = { navController.replaceWith(Routes.JOIN_PARTNER) },
        )
    }
    composable(Routes.JOIN_PARTNER) {
        JoinPartnerScreen(
            onJoined = { navController.replaceWith(Routes.NOTIFICATION_PERMISSION) },
            onInviteInstead = { navController.replaceWith(Routes.INVITE_PARTNER) },
        )
    }
    composable(Routes.NOTIFICATION_PERMISSION) {
        NotificationPermissionScreen(
            onDone = { navController.replaceWith(Routes.WAITING_FOR_PARTNER) },
        )
    }
    composable(Routes.WAITING_FOR_PARTNER) {
        WaitingForPartnerScreen(
            onReady = { navController.replaceWith(Routes.MATCH) },
        )
    }
}

/**
 * Navigate forward and drop the current screen from the back stack.
 *
 * `launchSingleTop` matters as much as the pop: these calls are fired from
 * LaunchedEffect blocks that can recompose, and without it a single "you're
 * ready" can push the match screen twice.
 */
private fun NavHostController.replaceWith(route: String) {
    val current = currentBackStackEntry?.destination?.route
    navigate(route) {
        launchSingleTop = true
        if (current != null) popUpTo(current) { inclusive = true }
    }
}

private fun androidx.navigation.NavGraphBuilder.mainGraph(navController: NavHostController) {
    composable(Routes.MATCH) {
        MatchScreen(
            onRateWatched = { navController.navigate(Routes.rateWatched(it)) },
            onScheduleWatch = { navController.navigate(Routes.reminder(it)) },
        )
    }
    composable(Routes.WATCHLIST) {
        PlaceholderScreen("Watchlist", "Ready / Waiting on you / Watched")
    }
    composable(Routes.US) {
        PlaceholderScreen("Us", "Streak, journey, compatibility, settings")
    }

    // These two stack on the Match tab rather than replacing it: they are
    // steps within today's match, and backing out belongs on the card.
    composable(
        route = Routes.REMINDER,
        arguments = listOf(navArgument(Routes.ARG_MATCH_ID) { type = NavType.StringType }),
    ) { entry ->
        ScheduleWatchScreen(
            matchId = entry.arguments?.getString(Routes.ARG_MATCH_ID).orEmpty(),
            onDone = { navController.popBackStack() },
        )
    }
    composable(
        route = Routes.RATE_WATCHED,
        arguments = listOf(navArgument(Routes.ARG_MATCH_ID) { type = NavType.StringType }),
    ) { entry ->
        RateWatchedScreen(
            matchId = entry.arguments?.getString(Routes.ARG_MATCH_ID).orEmpty(),
            onDone = { navController.popBackStack() },
        )
    }
}
