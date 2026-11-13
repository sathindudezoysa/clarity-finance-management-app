package com.example.clarity.presentation.goals

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.domain.util.GoalProgressPolicy
import com.example.clarity.domain.util.GoalStreakSummary
import com.example.clarity.presentation.common.AppIcons
import com.example.clarity.presentation.navigation.BottomNavDestination
import com.example.clarity.presentation.navigation.ClarityTopBar
import com.example.clarity.presentation.theme.ClarityTheme

@Composable
fun GoalsScreen(
    onAddGoal: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER")
    onEditGoal: (String) -> Unit = {},
    onAddProgress: (String) -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    topBar: @Composable () -> Unit = {
        ClarityTopBar(
            title = BottomNavDestination.GOALS.label,
            onProfileClick = onNavigateToProfile
        )
    },
    bottomBar: @Composable () -> Unit = {},
    viewModel: GoalsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshForCurrentDate()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    GoalsContent(
        state = state,
        onAddGoal = onAddGoal,
        onAddProgress = onAddProgress,
        onRetry = viewModel::retry,
        topBar = topBar,
        bottomBar = bottomBar
    )
}

@Composable
internal fun GoalsContent(
    state: GoalsState,
    onAddGoal: () -> Unit,
    onRetry: () -> Unit,
    onAddProgress: (String) -> Unit = {},
    topBar: @Composable () -> Unit = {
        ClarityTopBar(
            title = BottomNavDestination.GOALS.label,
            onProfileClick = {}
        )
    },
    bottomBar: @Composable () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val canCreate = state.ownerId != null && !state.isLoading
    Scaffold(
        topBar = topBar,
        bottomBar = bottomBar,
        containerColor = colors.background,
        floatingActionButton = {
            if (canCreate) {
                FloatingActionButton(
                    onClick = onAddGoal,
                    modifier = Modifier.semantics { contentDescription = "Add Goal" }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 14.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.error?.let { errorMessage ->
                    item(key = "goals-error-banner") {
                        GoalErrorBanner(
                            message = errorMessage,
                            onRetry = onRetry
                        )
                    }
                }

                if (state.isLoading) {
                    item(key = "goals-loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = colors.primary)
                        }
                    }
                } else if (state.ownerId != null) {
                    item(key = "goals-hero-summary") {
                        GoalsHeroSummaryCard(
                            summary = state.aggregateSummary,
                            streak = state.streakSummary
                        )
                    }

                    if (state.isEmpty) {
                        item(key = "goals-empty") {
                            GoalEmptyState(onAddGoal = onAddGoal)
                        }
                    } else {
                        if (state.activeGoals.isNotEmpty()) {
                            items(
                                items = state.activeGoals,
                                key = { it.id }
                            ) { goal ->
                                GoalCard(
                                    goal = goal,
                                    onAddProgress = { onAddProgress(goal.id) }
                                )
                            }
                        }

                        if (state.completedHistoryGoals.isNotEmpty()) {
                            item(key = "completed-goals-history-header") {
                                Text(
                                    text = "Completed Goals History",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSurface,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            items(
                                items = state.completedHistoryGoals,
                                key = { "history-${it.id}" }
                            ) { goal ->
                                GoalCard(
                                    goal = goal,
                                    onAddProgress = { onAddProgress(goal.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalsHeroSummaryCard(
    summary: GoalsAggregateSummaryUiModel,
    streak: GoalStreakSummary
) {
    val colors = MaterialTheme.colorScheme
    val currentStreakLabel = if (streak.currentStreakDays == 1) {
        "1 Day"
    } else {
        "${streak.currentStreakDays} Days"
    }
    val bestStreakLabel = if (streak.longestStreakDays == 1) {
        "1 Day"
    } else {
        "${streak.longestStreakDays} Days"
    }
    val cardDescription = buildString {
        append("Required monthly savings ${summary.monthlyRateFormatted} per month. ")
        append("Saved ${summary.savedFormatted}, ${summary.percentLabel} of ${summary.targetFormatted}. ")
        append("Current streak $currentStreakLabel, best streak $bestStreakLabel.")
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.primary,
        contentColor = colors.onPrimary,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = cardDescription }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
        ) {
            // Ambient decorative rings styled exclusively with theme colors
            Box(
                modifier = Modifier
                    .size(176.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 42.dp, y = 42.dp)
                    .clip(CircleShape)
                    .background(colors.secondary.copy(alpha = 0.08f))
            )
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = (-32).dp, y = (-44).dp)
                    .clip(CircleShape)
                    .background(colors.secondary.copy(alpha = 0.05f))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = AppIcons.DonutLarge,
                        contentDescription = null,
                        tint = colors.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = summary.monthlyRateFormatted,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "/ mo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.secondary,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = colors.secondary)) {
                                    append("Saved: ")
                                }
                                withStyle(
                                    SpanStyle(
                                        color = colors.onPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                ) {
                                    append(summary.savedFormatted)
                                }
                                withStyle(SpanStyle(color = colors.secondary)) {
                                    append(" (${summary.percentLabel})")
                                }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = colors.secondary)) {
                                    append("Target: ")
                                }
                                withStyle(
                                    SpanStyle(
                                        color = colors.onPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                ) {
                                    append(summary.targetFormatted)
                                }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    GoalRoundedProgressBar(
                        progress = summary.progressFraction,
                        trackColor = colors.onPrimary.copy(alpha = 0.20f),
                        indicatorColor = colors.secondary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StreakMetricTile(
                        icon = AppIcons.Flame,
                        label = "Current Streak",
                        value = currentStreakLabel,
                        modifier = Modifier.weight(1f)
                    )
                    StreakMetricTile(
                        icon = AppIcons.Trophy,
                        label = "Best Streak",
                        value = bestStreakLabel,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (streak.isAtRisk) {
                    Text(
                        text = GoalProgressPolicy.STREAK_AT_RISK_HINT,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun StreakMetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.onPrimary.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(colors.secondary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.secondary,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun GoalRoundedProgressBar(
    progress: Float,
    trackColor: androidx.compose.ui.graphics.Color,
    indicatorColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
    ) {
        if (clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(indicatorColor)
            )
        }
    }
}

@Composable
internal fun GoalErrorBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) {
                Text("Retry", color = colors.onErrorContainer, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun GoalEmptyState(onAddGoal: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface,
            contentColor = colors.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.ShieldPlus,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = GoalMessages.EMPTY_GOALS,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onAddGoal,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                ),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Create Goal")
            }
        }
    }
}

@Composable
private fun GoalCard(
    goal: GoalCardUiModel,
    onAddProgress: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface,
            contentColor = colors.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onAddProgress)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.ShieldPlus,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = goal.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Target: ${goal.targetFormatted}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = goal.percentValueLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = colors.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = MaterialTheme.typography.titleLarge.fontSize
                            )
                        ) {
                            append(goal.savedFormatted)
                        }
                        withStyle(
                            SpanStyle(
                                color = colors.onSurfaceVariant,
                                fontWeight = FontWeight.Normal,
                                fontSize = MaterialTheme.typography.bodySmall.fontSize
                            )
                        ) {
                            append(" / ${goal.targetFormatted}")
                        }
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = goal.timelineRemainingLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (goal.isOverdue && !goal.isCompleted) colors.error else colors.onSurfaceVariant
                )
            }

            GoalRoundedProgressBar(
                progress = goal.progressFraction,
                trackColor = colors.surfaceVariant,
                indicatorColor = colors.primary,
                modifier = Modifier.semantics {
                    contentDescription = "Goal progress ${goal.percentValueLabel}, saved ${goal.savedFormatted} of ${goal.targetFormatted}"
                }
            )

            Divider(color = colors.outlineVariant.copy(alpha = 0.35f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val footerText = when {
                    goal.isClosed -> goal.closedSummaryLabel ?: "Goal completed & closed"
                    goal.isCompleted -> "Goal reached • Locked — Ready to finish"
                    goal.isActive && goal.monthlyRateFormatted != null -> goal.monthlyRateFormatted
                    else -> "Due ${goal.targetDateFormatted}"
                }
                Text(
                    text = footerText,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onAddProgress,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    when {
                        goal.isClosed -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "History",
                                color = colors.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        goal.isCompleted -> {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Finish Goal",
                                color = colors.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        else -> {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Add Progress",
                                color = colors.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

private val previewGoalsState = GoalsState(
    isLoading = false,
    ownerId = "preview",
    goals = listOf(
        GoalCardUiModel(
            id = "1",
            name = "Emergency Safety Net",
            description = "Six months of essential expenses",
            currency = "USD",
            targetFormatted = "USD 15000.00",
            targetDateFormatted = "15 Dec 2026",
            daysRemainingLabel = "35 days left",
            timelineRemainingLabel = "5 weeks left",
            isOverdue = false,
            isSyncing = false,
            isActive = true,
            monthlyRateFormatted = "USD 250.00 / month",
            progressFraction = 0.966f,
            savedFormatted = "USD 14500.00",
            percentLabel = "96% saved",
            percentValueLabel = "96.6%",
            nextMilestoneLabel = "Next milestone: 100% (USD 15000.00)"
        ),
        GoalCardUiModel(
            id = "2",
            name = "Tokyo Spring Trip 2027",
            description = "",
            currency = "USD",
            targetFormatted = "USD 30000.00",
            targetDateFormatted = "10 Apr 2027",
            daysRemainingLabel = "156 days left",
            timelineRemainingLabel = "22 weeks left",
            isOverdue = false,
            isSyncing = false,
            isActive = true,
            monthlyRateFormatted = "USD 600.00 / month",
            progressFraction = 0.263f,
            savedFormatted = "USD 7900.00",
            percentLabel = "26% saved",
            percentValueLabel = "26.3%"
        )
    ),
    aggregateSummary = GoalsAggregateSummaryUiModel(
        currency = "USD",
        monthlyRateFormatted = "USD 850.00",
        savedFormatted = "USD 22400.00",
        targetFormatted = "USD 45000.00",
        percentLabel = "49.7%",
        progressFraction = 0.497f
    ),
    activeGoalCount = 2,
    streakSummary = GoalStreakSummary(
        currentStreakDays = 14,
        longestStreakDays = 32,
        isAtRisk = false,
        contributedToday = true
    )
)

@Preview(showBackground = true)
@Composable
private fun GoalsScreenPreview() {
    ClarityTheme {
        GoalsContent(
            state = previewGoalsState,
            onAddGoal = {},
            onRetry = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GoalsScreenDarkPreview() {
    ClarityTheme(darkTheme = true) {
        GoalsContent(
            state = previewGoalsState,
            onAddGoal = {},
            onRetry = {}
        )
    }
}
