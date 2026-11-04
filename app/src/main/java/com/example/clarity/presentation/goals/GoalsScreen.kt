package com.example.clarity.presentation.goals

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.presentation.navigation.BottomNavDestination
import com.example.clarity.presentation.navigation.ClarityTopBar
import com.example.clarity.presentation.theme.ClarityTheme

@Composable
fun GoalsScreen(
    onAddGoal: () -> Unit = {},
    onEditGoal: (String) -> Unit = {},
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
        onEditGoal = onEditGoal,
        onRetry = viewModel::retry,
        topBar = topBar,
        bottomBar = bottomBar
    )
}

@Composable
internal fun GoalsContent(
    state: GoalsState,
    onAddGoal: () -> Unit,
    onEditGoal: (String) -> Unit,
    onRetry: () -> Unit,
    topBar: @Composable () -> Unit = {
        ClarityTopBar(
            title = BottomNavDestination.GOALS.label,
            onProfileClick = {}
        )
    },
    bottomBar: @Composable () -> Unit = {}
) {
    val canCreate = state.ownerId != null && !state.isLoading
    Scaffold(
        topBar = topBar,
        bottomBar = bottomBar,
        floatingActionButton = {
            if (canCreate) {
                FloatingActionButton(
                    onClick = onAddGoal,
                    modifier = Modifier.semantics { contentDescription = "Add Goal" }
                ) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
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
                    top = padding.calculateTopPadding() + 16.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!state.isLoading && state.ownerId != null && state.activeGoalCount > 0) {
                    item(key = "goals-monthly-total") {
                        GoalsMonthlyTotalCard(
                            totals = state.totalMonthlyRates,
                            activeGoalCount = state.activeGoalCount
                        )
                    }
                }

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
                            CircularProgressIndicator()
                        }
                    }
                } else if (state.isEmpty && state.ownerId != null) {
                    item(key = "goals-empty") {
                        GoalEmptyState()
                    }
                } else {
                    items(
                        items = state.goals,
                        key = { it.id }
                    ) { goal ->
                        GoalCard(
                            goal = goal,
                            onEdit = { onEditGoal(goal.id) }
                        )
                    }
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
private fun GoalEmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = GoalMessages.EMPTY_GOALS,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GoalsMonthlyTotalCard(
    totals: List<MonthlyRateTotalUiModel>,
    activeGoalCount: Int
) {
    val colors = MaterialTheme.colorScheme
    val summaryDescription = buildString {
        append("Total required monthly savings. ")
        if (totals.isNotEmpty()) {
            append(totals.joinToString(", ") { it.totalFormatted })
            append(". ")
        }
        append("Across $activeGoalCount active goals")
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = summaryDescription },
        border = BorderStroke(1.dp, colors.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface,
            contentColor = colors.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Total required monthly savings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )
            totals.forEach { item ->
                Text(
                    text = item.totalFormatted,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary
                )
            }
            Text(
                text = "Across $activeGoalCount active goals",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GoalCard(
    goal: GoalCardUiModel,
    onEdit: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, colors.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface,
            contentColor = colors.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = goal.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (goal.isSyncing) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = colors.secondaryContainer,
                        contentColor = colors.onSecondaryContainer
                    ) {
                        Text(
                            text = "Syncing",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (goal.description.isNotEmpty()) {
                Text(
                    text = goal.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "Target: ${goal.targetFormatted}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )

            if (goal.isActive && goal.monthlyRateFormatted != null) {
                val rateLabel = "Required monthly savings: ${goal.monthlyRateFormatted}"
                Text(
                    text = rateLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.primary,
                    modifier = Modifier.semantics { contentDescription = rateLabel }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Target date: ${goal.targetDateFormatted}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (goal.isOverdue) colors.error else colors.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (goal.isOverdue) colors.errorContainer else colors.primaryContainer,
                    contentColor = if (goal.isOverdue) colors.onErrorContainer else colors.onPrimaryContainer
                ) {
                    Text(
                        text = goal.daysRemainingLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Edit")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GoalsScreenPreview() {
    ClarityTheme {
        GoalsContent(
            state = GoalsState(
                isLoading = false,
                ownerId = "preview",
                goals = listOf(
                    GoalCardUiModel(
                        id = "1",
                        name = "Emergency Fund",
                        description = "Six months of essential expenses",
                        currency = "LKR",
                        targetFormatted = "LKR 500000.00",
                        targetDateFormatted = "15 Jun 2027",
                        daysRemainingLabel = "120 days left",
                        isOverdue = false,
                        isSyncing = true,
                        isActive = true,
                        monthlyRateFormatted = "LKR 125000.00 / month"
                    ),
                    GoalCardUiModel(
                        id = "2",
                        name = "Laptop Upgrade",
                        description = "",
                        currency = "LKR",
                        targetFormatted = "LKR 300000.00",
                        targetDateFormatted = "01 Jan 2026",
                        daysRemainingLabel = "Overdue",
                        isOverdue = true,
                        isSyncing = false,
                        isActive = false,
                        monthlyRateFormatted = null
                    )
                ),
                totalMonthlyRates = listOf(
                    MonthlyRateTotalUiModel(
                        currency = "LKR",
                        totalFormatted = "LKR 125000.00 / month"
                    )
                ),
                activeGoalCount = 1
            ),
            onAddGoal = {},
            onEditGoal = {},
            onRetry = {}
        )
    }
}
