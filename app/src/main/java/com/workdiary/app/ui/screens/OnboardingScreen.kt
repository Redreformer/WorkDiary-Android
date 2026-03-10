package com.workdiary.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.workdiary.app.navigation.Screen

// ─────────────────────────────────────────────────────────────────────────────
//  OnboardingScreen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * First-run onboarding screen — converts iOS `OnboardingView.swift` to Jetpack Compose.
 *
 * ## Page flow
 * | Page | iOS equivalent               | Content                                          |
 * |------|------------------------------|--------------------------------------------------|
 * | 0    | Header section               | Welcome, app icon, title, subtitle               |
 * | 1    | `Toggle("Track in Hours…")` | Choose between days-based or hours-based tracking |
 * | 2    | `TextField("Amount…")`       | Enter annual leave allowance, then "Get Started" |
 *
 * A [HorizontalPager] provides the multi-step layout. Navigation state (current page, inputs)
 * is owned by [OnboardingViewModel]. The VM fires [OnboardingUiState.isComplete] = `true`
 * after saving prefs; this composable observes that flag and navigates to [Screen.Shell].
 *
 * @param navController The root [NavController] used to navigate to [Screen.Shell] on finish.
 * @param viewModel     [OnboardingViewModel] injected via Hilt.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    navController: NavController,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // ── Navigate to shell once onboarding is complete ─────────────────────
    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) {
            navController.navigate(Screen.Shell.route) {
                popUpTo(Screen.Onboarding.route) { inclusive = true }
            }
        }
    }

    // ── Pager state ────────────────────────────────────────────────────────
    val pagerState = rememberPagerState(
        initialPage = uiState.currentPage,
        pageCount   = { ONBOARDING_PAGE_COUNT },
    )

    // Keep pager and ViewModel in sync:
    // ViewModel → pager (e.g. "Next" button pressed)
    LaunchedEffect(uiState.currentPage) {
        if (pagerState.currentPage != uiState.currentPage) {
            pagerState.animateScrollToPage(uiState.currentPage)
        }
    }

    // Pager → ViewModel (user swipes manually)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewModel.onPageChanged(page)
        }
    }

    // ── Scaffold ───────────────────────────────────────────────────────────
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ── Pager content ──────────────────────────────────────────────
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 0.dp),
                userScrollEnabled = true,
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> TrackingModePage(
                        isHoursMode = uiState.isHoursMode,
                        onToggle    = viewModel::onHoursModeChanged,
                    )
                    2 -> AllowancePage(
                        isHoursMode    = uiState.isHoursMode,
                        allowanceText  = uiState.allowanceText,
                        onValueChange  = viewModel::onAllowanceTextChanged,
                        isSaving       = uiState.isSaving,
                        onGetStarted   = viewModel::completeOnboarding,
                    )
                    else -> Unit
                }
            }

            // ── Page indicator dots ────────────────────────────────────────
            PageIndicator(
                pageCount   = ONBOARDING_PAGE_COUNT,
                currentPage = uiState.currentPage,
                modifier    = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp),
            )

            // ── Navigation buttons ─────────────────────────────────────────
            OnboardingNavBar(
                currentPage  = uiState.currentPage,
                pageCount    = ONBOARDING_PAGE_COUNT,
                isSaving     = uiState.isSaving,
                onBack       = viewModel::onPreviousPage,
                onNext       = viewModel::onNextPage,
                onFinish     = viewModel::completeOnboarding,
                modifier     = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Page 0 — Welcome
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Welcome splash page — mirrors the iOS onboarding header with the `calendar.badge.clock`
 * SF Symbol, "Welcome to Leave Tracker" title, and subtitle.
 */
@Composable
private fun WelcomePage() {
    AnimatedVisibility(
        visible = true,
        enter   = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 },
    ) {
        Column(
            modifier             = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment  = Alignment.CenterHorizontally,
            verticalArrangement  = Arrangement.Center,
        ) {
            // App icon tile — mirrors iOS `Image(systemName: "calendar.badge.clock")`
            Box(
                modifier         = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    modifier           = Modifier.size(72.dp),
                    tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text       = "Welcome to\nLeave Tracker",
                style      = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                textAlign  = TextAlign.Center,
                color      = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text      = "First, let's set your yearly allowance.",
                style     = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Feature highlights
            FeatureRow(icon = Icons.Filled.DateRange,  text = "Track annual leave, lieu days & sick leave")
            Spacer(modifier = Modifier.height(12.dp))
            FeatureRow(icon = Icons.Filled.CalendarMonth, text = "Visualise your shifts month by month")
            Spacer(modifier = Modifier.height(12.dp))
            FeatureRow(icon = Icons.Filled.CheckCircle, text = "Simple, ad-free, and yours forever")
        }
    }
}

/**
 * A single row in the welcome feature list — icon + supporting text.
 */
@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
    Row(
        modifier     = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text  = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Page 1 — Tracking mode selection
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tracking-mode selection page — maps to the iOS `Toggle("Track in Hours (vs Days)", …)`.
 *
 * Presents two tappable cards: "Track in Days" and "Track in Hours". The selected card
 * is highlighted with the primary colour, matching a more visual Android pattern while
 * conveying the same choice as the iOS toggle.
 *
 * @param isHoursMode Current tracking-mode selection.
 * @param onToggle    Called with the new value when the user taps a card.
 */
@Composable
private fun TrackingModePage(
    isHoursMode: Boolean,
    onToggle:    (Boolean) -> Unit,
) {
    Column(
        modifier             = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment  = Alignment.CenterHorizontally,
        verticalArrangement  = Arrangement.Center,
    ) {
        Text(
            text  = "How do you track leave?",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text  = "Choose the unit that matches your employment contract. You can change this later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(40.dp))

        // ── Days card ───────────────────────────────────────────────────────
        TrackingModeCard(
            selected    = !isHoursMode,
            icon        = Icons.Filled.DateRange,
            title       = "Track in Days",
            subtitle    = "e.g. 25 days per year",
            onClick     = { onToggle(false) },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Hours card ──────────────────────────────────────────────────────
        TrackingModeCard(
            selected    = isHoursMode,
            icon        = Icons.Filled.AccessTime,
            title       = "Track in Hours",
            subtitle    = "e.g. 187.5 hours per year (standard UK)",
            onClick     = { onToggle(true) },
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Toggle mirror — keeps parity with iOS `Toggle` component.
        Row(
            modifier             = Modifier.fillMaxWidth(),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text  = "Track in Hours (vs Days)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Switch(
                checked         = isHoursMode,
                onCheckedChange = onToggle,
            )
        }
    }
}

/**
 * Selectable card for a tracking-mode option on [TrackingModePage].
 *
 * @param selected  Whether this card is the currently-active choice.
 * @param icon      Leading icon for the mode.
 * @param title     Short label.
 * @param subtitle  Descriptive example.
 * @param onClick   Click handler.
 */
@Composable
private fun TrackingModeCard(
    selected: Boolean,
    icon:     ImageVector,
    title:    String,
    subtitle: String,
    onClick:  () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = containerColor),
        shape    = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 0.dp),
    ) {
        Row(
            modifier          = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = contentColor,
                modifier           = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text  = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = contentColor,
                )
                Text(
                    text  = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Page 2 — Allowance entry
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Allowance entry page — maps to the iOS `HStack { Text(…) TextField("Amount"…) }` section.
 *
 * Displays a numeric text field labelled "Total Days" or "Total Hours" depending on [isHoursMode],
 * with a footer note mirroring the iOS "You can change this later in Settings." hint.
 *
 * @param isHoursMode    Whether the user is in hours mode (affects field label and default).
 * @param allowanceText  Current raw text field value.
 * @param onValueChange  Called with new text on every keystroke.
 * @param isSaving       Disables the "Get Started" button while DataStore writes are in-flight.
 * @param onGetStarted   Called when the user confirms their allowance and triggers [OnboardingViewModel.completeOnboarding].
 */
@Composable
private fun AllowancePage(
    isHoursMode:   Boolean,
    allowanceText: String,
    onValueChange: (String) -> Unit,
    isSaving:      Boolean,
    onGetStarted:  () -> Unit,
) {
    val focusManager  = LocalFocusManager.current
    val fieldLabel    = if (isHoursMode) "Total Hours" else "Total Days"
    val fieldHint     = if (isHoursMode) "e.g. 187.5" else "e.g. 25"

    Column(
        modifier             = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment  = Alignment.CenterHorizontally,
        verticalArrangement  = Arrangement.Center,
    ) {
        // Icon
        Box(
            modifier         = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = if (isHoursMode) Icons.Filled.AccessTime else Icons.Filled.DateRange,
                contentDescription = null,
                modifier           = Modifier.size(48.dp),
                tint               = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text      = "Your Yearly Allowance",
            style     = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color     = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text      = "Enter your annual leave entitlement${if (isHoursMode) " in hours" else " in days"}.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Allowance input field — mirrors iOS `TextField("Amount", value: $setupAmount, format: .number)`
        OutlinedTextField(
            value         = allowanceText,
            onValueChange = onValueChange,
            modifier      = Modifier.fillMaxWidth(),
            label         = { Text(fieldLabel) },
            placeholder   = { Text(fieldHint) },
            singleLine    = true,
            textStyle     = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction    = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text  = "You can change this later in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )

        Spacer(modifier = Modifier.height(40.dp))

        // ── Get Started button — mirrors iOS bold blue button ───────────────
        Button(
            onClick  = {
                focusManager.clearFocus()
                onGetStarted()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled  = !isSaving && allowanceText.isNotEmpty(),
            shape    = RoundedCornerShape(14.dp),
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier  = Modifier.size(24.dp),
                    color     = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text  = "Get Started",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Page indicator dots
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Horizontal row of dot indicators, one per pager page.
 *
 * The active page dot is wider and uses the primary colour; inactive dots use the outline
 * colour. Matches the visual convention of most Android onboarding flows.
 *
 * @param pageCount    Total number of pages.
 * @param currentPage  Index of the currently visible page.
 * @param modifier     Optional [Modifier].
 */
@Composable
private fun PageIndicator(
    pageCount:   Int,
    currentPage: Int,
    modifier:    Modifier = Modifier,
) {
    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(if (isActive) 24.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    ),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Navigation bar (Back / Next / Get Started)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bottom navigation row for the onboarding flow.
 *
 * - Page 0: "Skip" (text button) on the right — lets users jump straight to the allowance page.
 * - Pages 1+: "Back" icon button on the left; "Next" or "Get Started" on the right.
 * - The "Get Started" button is only shown on the last page but the actual save action
 *   is wired in [AllowancePage] as well (so the in-page button and this nav button both work).
 *
 * @param currentPage Index of the currently visible page.
 * @param pageCount   Total number of pages.
 * @param isSaving    Disables interaction while DataStore writes are in-flight.
 * @param onBack      Navigate to the previous page.
 * @param onNext      Navigate to the next page.
 * @param onFinish    Trigger [OnboardingViewModel.completeOnboarding] (last page only).
 * @param modifier    Optional [Modifier].
 */
@Composable
private fun OnboardingNavBar(
    currentPage: Int,
    pageCount:   Int,
    isSaving:    Boolean,
    onBack:      () -> Unit,
    onNext:      () -> Unit,
    onFinish:    () -> Unit,
    modifier:    Modifier = Modifier,
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        // Left: Back button (hidden on first page)
        if (currentPage > 0) {
            IconButton(onClick = onBack, enabled = !isSaving) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint               = MaterialTheme.colorScheme.onBackground,
                )
            }
        } else {
            // Placeholder so the right-side button stays aligned.
            Spacer(modifier = Modifier.size(48.dp))
        }

        // Right: Next / Get Started / Skip
        when {
            currentPage == 0 -> {
                TextButton(onClick = { repeat(pageCount - 1) { onNext() } }, enabled = !isSaving) {
                    Text("Skip setup")
                }
            }
            currentPage < pageCount - 1 -> {
                Button(onClick = onNext, enabled = !isSaving) {
                    Text("Next")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier           = Modifier.size(16.dp),
                    )
                }
            }
            else -> {
                // Last page — "Get Started" is also shown inside AllowancePage; this button
                // serves as a fallback for keyboard-dismissed state.
                Button(onClick = onFinish, enabled = !isSaving) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(16.dp),
                            color       = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Get Started")
                    }
                }
            }
        }
    }
}
