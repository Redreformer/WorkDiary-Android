package com.workdiary.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.workdiary.app.navigation.Screen

/**
 * Splash / launch screen — the first composable shown after cold start.
 *
 * ## Android 12+ SplashScreen API
 * The system-level SplashScreen (configured in [com.workdiary.app.MainActivity] via
 * `installSplashScreen()` and `WorkDiary.SplashTheme`) handles the very first frame.
 * This composable provides the branded *post-system-splash* animation: the app logo and
 * tagline scale-and-fade in (matching the iOS `SplashScreenView` animation), then the
 * screen auto-navigates after a short delay.
 *
 * ## Routing logic (matches iOS `AnnualLeaveTrackerApp.swift`)
 * - If `hasCompletedSetup == false` → navigate to [Screen.Onboarding] (first launch).
 * - If `hasCompletedSetup == true`  → navigate to [Screen.Shell] (returning user).
 *
 * Both navigation calls use `popUpTo(Screen.Splash) { inclusive = true }` so the splash
 * screen is removed from the back stack and the user cannot navigate back to it.
 *
 * @param navController Root [NavController] used to trigger the post-splash navigation.
 * @param viewModel     [SplashViewModel] that exposes the `hasCompletedSetup` preference.
 */
@Composable
fun SplashScreen(
    navController: NavController,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    // Collect preference state; null = still loading from DataStore.
    val hasCompletedSetup by viewModel.hasCompletedSetup.collectAsStateWithLifecycle(initialValue = null)

    // ── Animation state ────────────────────────────────────────────────────
    val scale   = remember { Animatable(initialValue = 0.8f) }
    val opacity = remember { Animatable(initialValue = 0.5f) }

    // ── Logo reveal animation (mirrors iOS scale 0.8→1.0, opacity 0.5→1.0) ─
    LaunchedEffect(Unit) {
        // Animate scale and opacity concurrently (matching iOS easeIn duration 0.8s).
        // We use kotlinx.coroutines launch-inside-LaunchedEffect pattern via the
        // Animatable API's parallel execution.
        val animSpec = tween<Float>(durationMillis = 800, easing = FastOutSlowInEasing)
        // Fire both animations together
        kotlinx.coroutines.coroutineScope {
            kotlinx.coroutines.launch { scale.animateTo(targetValue = 1f, animationSpec = animSpec) }
            kotlinx.coroutines.launch { opacity.animateTo(targetValue = 1f, animationSpec = animSpec) }
        }
    }

    // ── Navigate after splash delay (matches iOS 1.0 s delay) ────────────
    // Only fires once `hasCompletedSetup` is no longer null (DataStore loaded).
    LaunchedEffect(hasCompletedSetup) {
        if (hasCompletedSetup == null) return@LaunchedEffect // wait for DataStore

        // Ensure the reveal animation has had time to play (≥ 1 s total from cold start).
        kotlinx.coroutines.delay(1_000L)

        val destination = if (hasCompletedSetup == true) {
            Screen.Shell.route
        } else {
            Screen.Onboarding.route
        }

        navController.navigate(destination) {
            // Remove splash from back stack so back press exits the app.
            popUpTo(Screen.Splash.route) { inclusive = true }
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────────
    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier          = Modifier.fillMaxSize(),
            contentAlignment  = Alignment.Center,
        ) {
            Column(
                modifier             = Modifier
                    .scale(scale.value)
                    .alpha(opacity.value),
                horizontalAlignment  = Alignment.CenterHorizontally,
                verticalArrangement  = Arrangement.Center,
            ) {
                // App logo — tinted icon tile matching iOS AppLogo asset placement.
                // Replace this Icon with an Image(painter = painterResource(R.drawable.app_logo))
                // once the AppLogo asset is added to res/drawable.
                Box(
                    modifier         = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector       = Icons.Filled.CalendarMonth,
                        contentDescription = "Work Diary logo",
                        modifier          = Modifier.size(88.dp),
                        tint              = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // App title — matches iOS "My Work Diary" bold rounded text.
                Text(
                    text       = "My Work Diary",
                    style      = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize   = 32.sp,
                    ),
                    color      = MaterialTheme.colorScheme.onBackground,
                    textAlign  = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Tagline — matches iOS "Simple • Ad-Free • Forever" caption.
                Text(
                    text    = "Simple  •  Ad-Free  •  Forever",
                    style   = MaterialTheme.typography.bodySmall,
                    color   = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
