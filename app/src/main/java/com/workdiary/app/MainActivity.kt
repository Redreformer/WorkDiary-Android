package com.workdiary.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.workdiary.app.navigation.WorkDiaryNavGraph
import com.workdiary.app.ui.theme.WorkDiaryTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single [ComponentActivity] that hosts the entire Compose UI tree.
 *
 * ## Responsibilities
 * - Enables edge-to-edge rendering so the app extends under system bars.
 * - Resolves the user's dark/light mode preference from [MainViewModel] and passes it to
 *   [WorkDiaryTheme] so the colour scheme is applied globally.
 * - Hands off navigation to [WorkDiaryNavGraph], which owns the full back stack.
 *
 * ## Hilt
 * Annotated with [@AndroidEntryPoint][AndroidEntryPoint] so Hilt can inject dependencies into
 * this Activity (and into any [androidx.fragment.app.Fragment] it hosts, if ever needed).
 *
 * ## Architecture
 * This Activity is intentionally thin — no business logic lives here. All state is owned by
 * ViewModels and flows down through the composable tree.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Extends the app content into the system-bar insets. Compose handles inset padding
        // via WindowInsets modifiers rather than fitting system windows automatically.
        enableEdgeToEdge()

        setContent {
            // The MainViewModel is created once at Activity scope. It reads the user's
            // dark-mode preference from DataStore and exposes it as a StateFlow.
            val viewModel: MainViewModel = hiltViewModel()
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()

            WorkDiaryTheme(darkTheme = isDarkTheme) {
                // WorkDiaryNavGraph owns the NavController and the full navigation back stack.
                // All route transitions, argument passing, and deep-link handling live there.
                WorkDiaryNavGraph()
            }
        }
    }
}
