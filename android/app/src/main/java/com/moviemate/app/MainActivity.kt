package com.moviemate.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import com.moviemate.app.nav.MovieMateNavHost
import com.moviemate.app.nav.routeForDeepLink
import com.moviemate.app.ui.theme.MovieMateTheme

/**
 * Single activity host.
 *
 * Deep links from notifications arrive on two different paths: a cold start
 * delivers them through the launch Intent, while a warm app gets them via
 * onNewIntent. Both are handled here — they behave differently on Android and
 * missing the second one is a common bug.
 */
class MainActivity : ComponentActivity() {

    private var pendingDeepLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MovieMateMessagingService.ensureChannels(this)

        // Cold start from a notification tap.
        pendingDeepLink = intent.readDeepLinkRoute()

        setContent {
            MovieMateTheme {
                val navController = rememberNavController()
                MovieMateNavHost(
                    navController = navController,
                    pendingDeepLinkRoute = pendingDeepLink,
                    onDeepLinkConsumed = { pendingDeepLink = null },
                )
            }
        }
    }

    /** Warm start: the activity already exists, so the Intent comes in here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.readDeepLinkRoute()?.let { pendingDeepLink = it }
    }

    private fun Intent.readDeepLinkRoute(): String? {
        val target = getStringExtra(MovieMateMessagingService.EXTRA_DEEP_LINK_TARGET)
            ?: return null
        return routeForDeepLink(target)
    }
}
