package com.workdiary.app.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.workdiary.app.R

// ─────────────────────────────────────────────────────────────────────────────
//  Screen routes
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sealed hierarchy of all named navigation destinations in Work Diary.
 *
 * - Top-level destinations that appear in the bottom navigation bar implement [TopLevel].
 * - Other destinations (full-screen, overlays, etc.) are plain [Screen] objects.
 *
 * Route strings are stable identifiers; do not change them across releases without a migration
 * plan, as they may be persisted in the back-stack save state.
 */
sealed class Screen(val route: String) {

    // ── Initialisation flow ────────────────────────────────────────────────
    /** Animated splash screen shown at cold start. */
    data object Splash : Screen("splash")

    /** First-run onboarding (profile setup, allowance entry). */
    data object Onboarding : Screen("onboarding")

    // ── Main shell — hosts the bottom navigation bar ───────────────────────
    /** Outer scaffold that renders the [NavigationBar] and delegates content to [NavGraph]. */
    data object Shell : Screen("shell")

    // ── Bottom-nav tabs  (iOS: Calendar / Holidays / Balance / Settings) ───

    /**
     * Monthly/weekly/daily calendar view. Primary entry point for day-to-day shift logging.
     * Maps to iOS `CalendarView`.
     */
    data object Calendar : Screen("calendar"), TopLevel {
        override val labelRes  = R.string.nav_calendar
        override val icon      = Icons.Filled.CalendarMonth
    }

    /**
     * Booked leave list (Annual, Allocated, Personal).
     * Maps to iOS `BookedView` ("Holidays" tab).
     */
    data object BookedLeave : Screen("booked_leave"), TopLevel {
        override val labelRes  = R.string.nav_holidays
        override val icon      = Icons.AutoMirrored.Filled.List
    }

    /**
     * Leave balance dashboard with circular progress chart and lieu tracker.
     * Maps to iOS `BalanceView` ("Balance" tab).
     */
    data object Dashboard : Screen("dashboard"), TopLevel {
        override val labelRes  = R.string.nav_balance
        override val icon      = Icons.Filled.DonutLarge
    }

    /**
     * App settings: profiles, appearance, notifications, PDF duty-board management, data reset.
     * Maps to iOS `SettingsView`.
     */
    data object Settings : Screen("settings"), TopLevel {
        override val labelRes  = R.string.nav_settings
        override val icon      = Icons.Filled.Settings
    }

    // ── Full-screen overlays / detail screens ─────────────────────────────

    /**
     * Full-screen pinch-to-zoom photo viewer with swipe between photo slots.
     * Launched from [Calendar] when a user taps a day-photo thumbnail.
     * Maps to iOS `ZoomableImageView`.
     *
     * Route arguments:
     * - `date`  — ISO-8601 date string (yyyy-MM-dd) identifying the calendar day.
     * - `index` — which photo slot (0, 1, or 2) to show first.
     */
    data object ZoomImage : Screen("zoom_image/{date}/{index}") {
        const val ARG_DATE  = "date"
        const val ARG_INDEX = "index"

        /** Builds the concrete route string for a specific date + slot. */
        fun createRoute(date: String, index: Int) = "zoom_image/$date/$index"
    }
}

/**
 * Marker interface for destinations shown as tabs in the [NavigationBar].
 *
 * Implementing objects must supply a string resource ID for the tab label and an [ImageVector]
 * for the tab icon. The ordered list [topLevelScreens] controls tab order.
 */
interface TopLevel {
    @get:StringRes
    val labelRes: Int
    val icon: ImageVector
}

/**
 * Ordered list of [TopLevel] screens — drives both the [NavigationBar] item order and the
 * back-stack start-destination look-up.
 */
val topLevelScreens: List<Screen> = listOf(
    Screen.Calendar,
    Screen.BookedLeave,
    Screen.Dashboard,
    Screen.Settings,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Shell composable (bottom nav scaffold)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Outer scaffold that owns the bottom [NavigationBar] and the inner [NavHost] for the
 * four primary tabs.
 *
 * The bottom bar is hidden on non-tab destinations (e.g. [Screen.ZoomImage]) by checking
 * whether the current destination is a [TopLevel] route. An outer [NavHostController] passed
 * in from [WorkDiaryNavGraph] handles full-screen overlays that sit outside this scaffold.
 *
 * @param outerNavController The root nav controller that can navigate to full-screen routes
 *                           (e.g. [Screen.ZoomImage]) above this scaffold.
 */
@Composable
fun MainShell(outerNavController: NavHostController) {
    val tabNavController = rememberNavController()
    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                topLevelScreens.forEach { screen ->
                    val topLevel = screen as TopLevel
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == screen.route } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick  = {
                            tabNavController.navigate(screen.route) {
                                // Pop up to the start destination so the back stack
                                // doesn't build up with each tab switch.
                                popUpTo(tabNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        },
                        icon  = { Icon(topLevel.icon, contentDescription = null) },
                        label = { Text(text = stringResource(topLevel.labelRes)) },
                        alwaysShowLabel = true,
                    )
                }
            }
        }
    ) { innerPadding ->
        // Inner NavHost: tab-level navigation only.
        // Full-screen routes (ZoomImage, etc.) are handled by the outer NavHost.
        NavHost(
            navController    = tabNavController,
            startDestination = Screen.Calendar.route,
            modifier         = Modifier.padding(innerPadding),
            enterTransition  = { fadeIn(animationSpec = tween(200)) },
            exitTransition   = { fadeOut(animationSpec = tween(200)) },
        ) {
            composable(Screen.Calendar.route) {
                // TODO Phase 4: Replace with CalendarScreen(navController = outerNavController)
                PlaceholderScreen(label = "Calendar")
            }

            composable(Screen.BookedLeave.route) {
                // TODO Phase 5: Replace with BookedLeaveScreen()
                PlaceholderScreen(label = "Holidays")
            }

            composable(Screen.Dashboard.route) {
                // TODO Phase 6: Replace with DashboardScreen()
                PlaceholderScreen(label = "Balance")
            }

            composable(Screen.Settings.route) {
                // TODO Phase 7: Replace with SettingsScreen()
                PlaceholderScreen(label = "Settings")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Root nav graph
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Root [NavHost] for the entire application.
 *
 * Covers all navigation destinations including the initialisation flow (Splash → Onboarding)
 * and full-screen overlays (ZoomImage) that sit above the tab shell.
 *
 * The `startDestination` is always [Screen.Splash]; the splash composable itself decides
 * (based on the `hasCompletedSetup` preference) whether to navigate to [Screen.Onboarding]
 * or [Screen.Shell].
 *
 * @param navController The root [NavHostController], typically created in [MainActivity].
 */
@Composable
fun WorkDiaryNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Splash.route,
    ) {

        // ── Splash ──────────────────────────────────────────────────────────
        composable(
            route = Screen.Splash.route,
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition  = { fadeOut(animationSpec = tween(300)) },
        ) {
            // TODO Phase 3b: Replace with SplashScreen(navController)
            PlaceholderScreen(label = "Splash")
        }

        // ── Onboarding ──────────────────────────────────────────────────────
        composable(
            route = Screen.Onboarding.route,
            enterTransition = {
                slideIntoContainer(
                    towards       = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(400),
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards       = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(400),
                )
            },
        ) {
            // TODO Phase 8: Replace with OnboardingScreen(navController)
            PlaceholderScreen(label = "Onboarding")
        }

        // ── Main shell (bottom nav) ──────────────────────────────────────────
        composable(Screen.Shell.route) {
            MainShell(outerNavController = navController)
        }

        // ── Full-screen ZoomImage ────────────────────────────────────────────
        composable(
            route = Screen.ZoomImage.route,
            arguments = listOf(
                navArgument(Screen.ZoomImage.ARG_DATE)  { type = NavType.StringType },
                navArgument(Screen.ZoomImage.ARG_INDEX) { type = NavType.IntType; defaultValue = 0 },
            ),
            enterTransition = { fadeIn(animationSpec = tween(250)) },
            exitTransition  = { fadeOut(animationSpec = tween(250)) },
        ) { backStackEntry ->
            val date  = backStackEntry.arguments?.getString(Screen.ZoomImage.ARG_DATE)  ?: ""
            val index = backStackEntry.arguments?.getInt(Screen.ZoomImage.ARG_INDEX)    ?: 0
            // TODO Phase 4: Replace with ZoomImageScreen(date, index, navController)
            PlaceholderScreen(label = "Zoom · $date · slot $index")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Placeholder — remove when real screens are wired in
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Temporary placeholder composable used during phased development.
 *
 * Displays the destination [label] centred on screen. Replace each call site with the real
 * screen composable as each phase is completed.
 *
 * @param label Human-readable name of the destination, shown for debugging.
 */
@Composable
private fun PlaceholderScreen(label: String) {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "🚧  $label  🚧")
    }
}
