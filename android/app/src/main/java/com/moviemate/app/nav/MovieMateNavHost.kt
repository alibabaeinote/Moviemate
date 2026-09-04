package com.moviemate.app.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.moviemate.app.ui.components.BottomNavItem
import com.moviemate.app.ui.components.MovieMateBottomNav
import com.moviemate.app.ui.screens.PlaceholderScreen
import com.moviemate.app.ui.screens.auth.ForgotPasswordScreen
import com.moviemate.app.ui.screens.auth.SignInScreen
import com.moviemate.app.ui.screens.auth.SignUpScreen
import com.moviemate.app.ui.screens.auth.WelcomeScreen
import com.moviemate.app.ui.theme.Space

/**
 * The three main tabs. Icons are placeholders from the Material set; the design
 * system calls for a custom 24x24 stroke set (§6), which is a design asset this
 * scaffold does not ship.
 */
private val bottomNavItems = listOf(
    BottomNavItem("Match", Icons.Outlined.DateRange, Routes.MATCH),
    BottomNavItem("Watchlist", Icons.Outlined.List, Routes.WATCHLIST),
    BottomNavItem("Us", Icons.Outlined.Person, Routes.US),
)

private val tabRoutes = bottomNavItems.map { it.route }.toSet()

@Composable
fun MovieMateNavHost(
    navController: NavHostController,
    pendingDeepLinkRoute: String?,
    onDeepLinkConsumed: () -> Unit,
    startDestination: String = Routes.WELCOME,
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
            authGraph(navController)
            onboardingGraph()
            mainGraph()
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
            onSignedUp = { navController.navigate(Routes.ONBOARDING_RATE) },
            onSignInInstead = { navController.navigate(Routes.SIGN_IN) },
        )
    }
    composable(Routes.SIGN_IN) {
        SignInScreen(
            onSignedIn = { navController.navigate(Routes.MATCH) },
            onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
            onSignUpInstead = { navController.navigate(Routes.SIGN_UP) },
        )
    }
    composable(Routes.FORGOT_PASSWORD) {
        ForgotPasswordScreen(onDone = { navController.popBackStack() })
    }
}

private fun androidx.navigation.NavGraphBuilder.onboardingGraph() {
    composable(Routes.ONBOARDING_RATE) {
        PlaceholderScreen("Rate a few films", "Taste Dial deck, backed by TMDB")
    }
    composable(Routes.INVITE_PARTNER) {
        PlaceholderScreen("Invite your partner", "Invite code from createPair")
    }
    composable(Routes.JOIN_PARTNER) {
        PlaceholderScreen("Join with a code", "Calls joinPair")
    }
    composable(Routes.NOTIFICATION_PERMISSION) {
        PlaceholderScreen("Stay in the loop", "POST_NOTIFICATIONS request")
    }
    composable(Routes.WAITING_FOR_PARTNER) {
        PlaceholderScreen("Waiting on your partner", "Listens to pair.aBothOnboarded")
    }
}

private fun androidx.navigation.NavGraphBuilder.mainGraph() {
    composable(Routes.MATCH) {
        PlaceholderScreen("Tonight's match", "Match card with mutual We're in")
    }
    composable(Routes.WATCHLIST) {
        PlaceholderScreen("Watchlist", "Ready / Waiting on you / Watched")
    }
    composable(Routes.US) {
        PlaceholderScreen("Us", "Streak, journey, compatibility, settings")
    }
    composable(Routes.REMINDER) {
        PlaceholderScreen("You're both set", "Suggested time + reminder")
    }
    composable(Routes.RATE_WATCHED) {
        PlaceholderScreen("How was it?", "Taste Dial, both partners")
    }
}
