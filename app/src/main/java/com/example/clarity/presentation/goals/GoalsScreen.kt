package com.example.clarity.presentation.goals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.presentation.theme.ClarityTheme

@Composable
fun GoalsScreen(
    onAddGoal: () -> Unit = {},
    onEditGoal: (String) -> Unit = {},
    onUpdateProgress: (String) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    viewModel: GoalsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GoalsContent(
        state = state,
        onAddGoal = onAddGoal,
        onEditGoal = onEditGoal,
        onUpdateProgress = onUpdateProgress,
        onRetry = viewModel::retry,
        onDismissRejected = viewModel::dismissRejectedOperation,
        bottomBar = bottomBar
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalsContent(
    state: GoalsState,
    onAddGoal: () -> Unit,
    onEditGoal: (String) -> Unit,
    onUpdateProgress: (String) -> Unit,
    onRetry: () -> Unit,
    onDismissRejected: (String) -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val canCreate = state.ownerId != null && !state.isLoading
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Goals",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    if (canCreate) {
                        TextButton(onClick = onAddGoal) {
                            Text("Add Goal")
                        }
                    }
                }
            )
        },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.error?.let { errorMessage ->
                    item(key = "goals-error-banner") {
                        GoalErrorBanner(
                            message = errorMessage,
                            onRetry = onRetry
                        )
                    }
                }

                items(
                    items = state.rejectedOperations,
                    key = { "rejected-${it.id}" }
                ) { rejected ->
                    GoalRejectedOperationBanner(
                        message = rejected.message,
                        onDismiss = { onDismissRejected(rejected.id) }
                    )
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
                        GoalEmptyState(onAddGoal = onAddGoal)
                    }
                } else {
                    if (state.activeGoals.isNotEmpty()) {
                        item(key = "section-active-goals") {
                            GoalSectionHeader("Active Goals")
                        }
                        items(
                            items = state.activeGoals,
                            key = { "active-${it.id}" }
                        ) { goal ->
                            GoalCard(
                                goal = goal,
                                onUpdateProgress = { onUpdateProgress(goal.id) },
                                onEdit = { onEditGoal(goal.id) }
                            )
                        }
                    }

                    if (state.completedGoals.isNotEmpty()) {
                        item(key = "section-completed-goals") {
                            GoalSectionHeader("Completed Goals")
                        }
                        items(
                            items = state.completedGoals,
                            key = { "completed-${it.id}" }
                        ) { goal ->
                            GoalCard(
                                goal = goal,
                                onUpdateProgress = { onUpdateProgress(goal.id) },
                                onEdit = { onEditGoal(goal.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
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
private fun GoalRejectedOperationBanner(
    message: String,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = colors.onErrorContainer, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun GoalEmptyState(onAddGoal: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = GoalMessages.EMPTY_GOALS,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onAddGoal) {
                Text("Add Goal")
            }
        }
    }
}

@Composable
private fun GoalCard(
    goal: GoalCardUiModel,
    onUpdateProgress: () -> Unit,
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
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (goal.isCompleted) colors.tertiaryContainer else colors.primaryContainer,
                    contentColor = if (goal.isCompleted) colors.onTertiaryContainer else colors.onPrimaryContainer
                ) {
                    Text(
                        text = goal.statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (goal.description.isNotEmpty()) {
                Text(
                    text = goal.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved: ${goal.savedFormatted}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${goal.progressPercent}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (goal.isCompleted) colors.tertiary else colors.primary
                )
            }

            LinearProgressIndicator(
                progress = goal.progressFraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (goal.isCompleted) colors.tertiary else colors.primary,
                trackColor = colors.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Target: ${goal.targetFormatted}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = "Remaining: ${goal.remainingFormatted}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Deadline: ${goal.deadlineFormatted}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (goal.isOverdue) colors.error else colors.onSurfaceVariant
                )
                if (goal.isOverdue) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = colors.errorContainer,
                        contentColor = colors.onErrorContainer
                    ) {
                        Text(
                            text = "Overdue",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onEdit) {
                    Text("Edit")
                }
                Button(onClick = onUpdateProgress) {
                    Text("Update progress")
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
                activeGoals = listOf(
                    GoalCardUiModel(
                        id = "1",
                        name = "Emergency Fund",
                        description = "Six months of essential expenses",
                        currency = "LKR",
                        targetFormatted = "LKR 500000.00",
                        savedFormatted = "LKR 200000.00",
                        remainingFormatted = "LKR 300000.00",
                        deadlineFormatted = "15 Jun 2027",
                        isOverdue = false,
                        progressPercent = 40,
                        progressFraction = 0.4f,
                        statusLabel = "Active",
                        isCompleted = false,
                        isSyncing = true
                    )
                ),
                completedGoals = listOf(
                    GoalCardUiModel(
                        id = "2",
                        name = "Laptop Upgrade",
                        description = "",
                        currency = "LKR",
                        targetFormatted = "LKR 300000.00",
                        savedFormatted = "LKR 300000.00",
                        remainingFormatted = "LKR 0.00",
                        deadlineFormatted = "01 Jan 2027",
                        isOverdue = false,
                        progressPercent = 100,
                        progressFraction = 1f,
                        statusLabel = "Completed",
                        isCompleted = true,
                        isSyncing = false
                    )
                )
            ),
            onAddGoal = {},
            onEditGoal = {},
            onUpdateProgress = {},
            onRetry = {},
            onDismissRejected = {}
        )
    }
}
