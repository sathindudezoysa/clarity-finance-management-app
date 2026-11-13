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
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                        if (state.ongoingGoals.isNotEmpty()) {
                            item(key = "ongoing-goals-header") {
                                GoalSectionHeader(
                                    icon = AppIcons.Psychology,
                                    title = "Ongoing",
                                    count = state.ongoingGoals.size,
                                    tint = colors.primary
                                )
                            }
                            items(
                                items = state.ongoingGoals,
                                key = { it.id }
                            ) { goal ->
                                OngoingGoalCard(
                                    goal = goal,
                                    onAddProgress = { onAddProgress(goal.id) }
                                )
                            }
                        }

                        if (state.completedGoals.isNotEmpty()) {
                            item(key = "completed-goals-history-header") {
                                GoalSectionHeader(
                                    icon = AppIcons.Trophy,
                                    title = "Completed",
                                    count = state.completedGoals.size,
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            item(key = "completed-goals-compact-list") {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    state.completedGoals.forEach { goal ->
                                        CompletedGoalCompactCard(
                                            goal = goal,
                                            onClick = { onAddProgress(goal.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalSectionHeader(
    icon: ImageVector,
    title: String,
    count: Int,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface
        )
        Surface(
            shape = CircleShape,
            color = colors.surfaceVariant,
            contentColor = colors.onSurfaceVariant
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun GoalsHeroSummaryCard(
    summary: GoalsAggregateSummaryUiModel,
    streak: GoalStreakSummary
) {
    val colors = MaterialTheme.colorScheme
    val currentStreakLabel = "${streak.currentStreakDays}d"
    val bestStreakLabel = "${streak.longestStreakDays}d"
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Psychology,
                            contentDescription = null,
                            tint = colors.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Icon(
                            imageVector = AppIcons.TrendingUp,
                            contentDescription = null,
                            tint = colors.onPrimary.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = colors.onPrimary.copy(alpha = 0.14f),
                        contentColor = colors.onPrimary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.DonutLarge,
                                contentDescription = null,
                                tint = colors.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = summary.percentLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
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
                        text = "/mo",
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = AppIcons.Savings,
                                contentDescription = null,
                                tint = colors.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = summary.savedFormatted,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Target,
                                contentDescription = null,
                                tint = colors.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = summary.targetFormatted,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    GoalRoundedProgressBar(
                        progress = summary.progressFraction,
                        trackColor = colors.onPrimary.copy(alpha = 0.20f),
                        indicatorColor = colors.secondary,
                        height = 10.dp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StreakMetricTile(
                        icon = AppIcons.Flame,
                        value = currentStreakLabel,
                        trailingBadgeIcon = if (streak.isAtRisk) AppIcons.Lightbulb else null,
                        modifier = Modifier.weight(1f)
                    )
                    StreakMetricTile(
                        icon = AppIcons.Trophy,
                        value = bestStreakLabel,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StreakMetricTile(
    icon: ImageVector,
    value: String,
    modifier: Modifier = Modifier,
    trailingBadgeIcon: ImageVector? = null
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.onPrimary.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(colors.secondary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.secondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailingBadgeIcon != null) {
            Icon(
                imageVector = trailingBadgeIcon,
                contentDescription = "Streak at risk",
                tint = colors.secondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
internal fun GoalRoundedProgressBar(
    progress: Float,
    trackColor: Color,
    indicatorColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    showMilestoneTicks: Boolean = false
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
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
        if (showMilestoneTicks) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.55f))
                    )
                }
            }
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
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface,
            contentColor = colors.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CognitiveCircleIcon(icon = AppIcons.Psychology, size = 40.dp, iconSize = 20.dp)
                CognitiveCircleIcon(icon = AppIcons.Target, size = 52.dp, iconSize = 26.dp, emphasized = true)
                CognitiveCircleIcon(icon = AppIcons.Savings, size = 40.dp, iconSize = 20.dp)
            }
            Text(
                text = "New Goal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
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
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create")
            }
        }
    }
}

@Composable
private fun CognitiveCircleIcon(
    icon: ImageVector,
    size: Dp,
    iconSize: Dp,
    emphasized: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (emphasized) colors.primaryContainer else colors.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (emphasized) colors.onPrimaryContainer else colors.primary,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun OngoingGoalCard(
    goal: GoalCardUiModel,
    onAddProgress: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val compactRate = goal.monthlyRateFormatted?.replace(" / month", "/mo")
    val compactTimeline = goal.timelineRemainingLabel
        .replace(" weeks left", "w")
        .replace(" days left", "d")
        .replace(" day left", "d")

    Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.25f)),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface,
            contentColor = colors.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onAddProgress)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(colors.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Target,
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = goal.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (goal.isSyncing) {
                            Icon(
                                imageVector = AppIcons.Sync,
                                contentDescription = "Syncing",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Calendar,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = goal.targetDateFormatted,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.TrendingUp,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = goal.percentValueLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = AppIcons.Savings,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = goal.savedFormatted,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Target,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = goal.targetFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            GoalRoundedProgressBar(
                progress = goal.progressFraction,
                trackColor = colors.surfaceVariant,
                indicatorColor = colors.primary,
                height = 12.dp,
                showMilestoneTicks = true,
                modifier = Modifier.semantics {
                    contentDescription = "Goal progress ${goal.percentValueLabel}, saved ${goal.savedFormatted} of ${goal.targetFormatted}"
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (compactRate != null) {
                        GoalCognitiveMetricChip(
                            icon = AppIcons.TrendingUp,
                            text = compactRate,
                            tint = colors.primary
                        )
                    }
                    GoalCognitiveMetricChip(
                        icon = AppIcons.Schedule,
                        text = compactTimeline,
                        tint = if (goal.isOverdue) colors.error else colors.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalButton(
                    onClick = onAddProgress,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = AppIcons.Savings,
                        contentDescription = "Add Progress",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalCognitiveMetricChip(
    icon: ImageVector,
    text: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colors.surfaceVariant.copy(alpha = 0.65f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompletedGoalCompactCard(
    goal: GoalCardUiModel,
    onClick: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val isLockedReadyToFinish = goal.isCompleted && !goal.isClosed
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceVariant.copy(alpha = 0.45f),
        contentColor = colors.onSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isLockedReadyToFinish) {
                colors.primary.copy(alpha = 0.4f)
            } else {
                colors.outlineVariant.copy(alpha = 0.35f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        if (isLockedReadyToFinish) colors.primaryContainer else colors.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLockedReadyToFinish) Icons.Default.Lock else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (isLockedReadyToFinish) colors.onPrimaryContainer else colors.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = goal.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Savings,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = goal.savedFormatted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = if (isLockedReadyToFinish) colors.primary else colors.secondaryContainer,
                contentColor = if (isLockedReadyToFinish) colors.onPrimary else colors.primary
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isLockedReadyToFinish) AppIcons.SwapHoriz else AppIcons.Trophy,
                        contentDescription = if (isLockedReadyToFinish) "Finish Goal" else "Completed",
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (isLockedReadyToFinish) "Finish" else "100%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
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
