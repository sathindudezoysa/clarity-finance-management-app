package com.example.clarity.presentation.goals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.presentation.theme.ClarityTheme

@Composable
fun GoalProgressScreen(
    onBack: () -> Unit,
    viewModel: GoalProgressViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GoalProgressContent(
        state = state,
        onBack = onBack,
        onRetryLoad = viewModel::loadAndObserve,
        onAmountChanged = viewModel::onAmountChanged,
        onAddContribution = viewModel::addContribution
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalProgressContent(
    state: GoalProgressState,
    onBack: () -> Unit,
    onRetryLoad: () -> Unit,
    onAmountChanged: (String) -> Unit,
    onAddContribution: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Goal Progress",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.fatalError != null -> {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GoalErrorBanner(
                            message = state.fatalError,
                            onRetry = onRetryLoad
                        )
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text("Back to Goals")
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        state.formError?.let { errorMsg ->
                            item(key = "progress-form-error") {
                                GoalErrorBanner(
                                    message = errorMsg,
                                    onRetry = onAddContribution
                                )
                            }
                        }

                        if (state.celebrationTitle != null && state.celebrationMessage != null) {
                            item(key = "progress-celebration") {
                                GoalMilestoneCelebrationCard(
                                    title = state.celebrationTitle,
                                    message = state.celebrationMessage
                                )
                            }
                        }

                        item(key = "progress-summary-card") {
                            GoalProgressSummaryCard(state = state)
                        }

                        item(key = "progress-add-card") {
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
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Add Progress Today",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (state.availableToContributeFormatted.isNotEmpty()) {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = colors.surfaceVariant,
                                            contentColor = colors.onSurfaceVariant,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = "Available to add: ${state.availableToContributeFormatted}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colors.onSurface
                                                )
                                                if (state.availabilityLimitLabel.isNotEmpty()) {
                                                    Text(
                                                        text = state.availabilityLimitLabel,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = colors.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    state.fundingError?.let { fundingMsg ->
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = colors.errorContainer,
                                            contentColor = colors.onErrorContainer,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = fundingMsg,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                    OutlinedTextField(
                                        value = state.amountInput,
                                        onValueChange = onAmountChanged,
                                        label = { Text("Contribution Amount (${state.currency}) *") },
                                        prefix = { Text("${state.currency} ") },
                                        singleLine = true,
                                        enabled = !state.isSaving && state.fundingError == null,
                                        isError = state.amountError != null,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        supportingText = {
                                            val err = state.amountError
                                            if (err != null) {
                                                Text(err)
                                            } else {
                                                Text("Deducted from Cash Wallet today in ${state.currency}")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Button(
                                            onClick = onAddContribution,
                                            enabled = state.isAddEnabled,
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("Add")
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "progress-milestones-header") {
                            Text(
                                text = "Milestones",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        items(
                            items = state.milestones,
                            key = { "milestone-${it.percent}" }
                        ) { milestone ->
                            GoalMilestoneRowCard(milestone = milestone)
                        }

                        item(key = "progress-history-header") {
                            Text(
                                text = "Contribution History",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        if (state.contributions.isEmpty()) {
                            item(key = "progress-history-empty") {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    border = BorderStroke(1.dp, colors.outlineVariant),
                                    colors = CardDefaults.cardColors(containerColor = colors.surface)
                                ) {
                                    Text(
                                        text = "No contributions recorded yet. Add your first contribution above!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        } else {
                            items(
                                items = state.contributions,
                                key = { it.id }
                            ) { item ->
                                GoalContributionRowCard(item = item)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalMilestoneCelebrationCard(
    title: String,
    message: String
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$title. $message" },
        shape = MaterialTheme.shapes.medium,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Star,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun GoalProgressSummaryCard(state: GoalProgressState) {
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
            Text(
                text = state.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (state.description.isNotEmpty()) {
                Text(
                    text = state.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            Text(
                text = "Saved: ${state.savedFormatted} of ${state.targetFormatted}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (state.isCompleted) colors.primaryContainer else colors.surfaceVariant,
                    contentColor = if (state.isCompleted) colors.onPrimaryContainer else colors.onSurfaceVariant
                ) {
                    Text(
                        text = state.percentLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            LinearProgressIndicator(
                progress = state.progressFraction.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .semantics {
                        contentDescription = "Goal progress ${state.percentLabel}, saved ${state.savedFormatted} of ${state.targetFormatted}"
                    },
                color = colors.primary,
                trackColor = colors.surfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Target date: ${state.targetDateFormatted}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.isOverdue) colors.error else colors.onSurfaceVariant
                )
                Text(
                    text = state.daysRemainingLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (state.isOverdue) colors.error else colors.primary
                )
            }
        }
    }
}

@Composable
private fun GoalMilestoneRowCard(milestone: GoalMilestoneUiModel) {
    val colors = MaterialTheme.colorScheme
    val statusText = if (milestone.isReached) "Reached" else "Upcoming"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${milestone.title}, $statusText, target ${milestone.requiredFormatted}"
            },
        border = BorderStroke(
            1.dp,
            if (milestone.isReached) colors.primary else colors.outlineVariant
        ),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface,
            contentColor = colors.onSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (milestone.isReached) Icons.Default.CheckCircle else Icons.Outlined.Star,
                contentDescription = null,
                tint = if (milestone.isReached) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${milestone.title} (${milestone.requiredFormatted})",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = milestone.motivationText,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = if (milestone.isReached) colors.primaryContainer else colors.surfaceVariant,
                contentColor = if (milestone.isReached) colors.onPrimaryContainer else colors.onSurfaceVariant
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun GoalContributionRowCard(item: GoalContributionItemUiModel) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, colors.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface,
            contentColor = colors.onSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.amountFormatted,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = item.dateFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            if (item.isSyncing) {
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
    }
}

@Preview(showBackground = true)
@Composable
private fun GoalProgressScreenPreview() {
    ClarityTheme {
        GoalProgressContent(
            state = GoalProgressState(
                goalId = "1",
                isLoading = false,
                name = "Emergency Fund",
                description = "Six months of expenses",
                currency = "LKR",
                savedFormatted = "LKR 250000.00",
                targetFormatted = "LKR 500000.00",
                targetDateFormatted = "15 Jun 2027",
                daysRemainingLabel = "120 days left",
                progressFraction = 0.5f,
                percentLabel = "50% saved",
                celebrationTitle = "50% Milestone",
                celebrationMessage = "Halfway there! You have reached 50% of your goal.",
                milestones = listOf(
                    GoalMilestoneUiModel(25, "25% Milestone", "LKR 125000.00", "Great start!", true),
                    GoalMilestoneUiModel(50, "50% Milestone", "LKR 250000.00", "Halfway there!", true),
                    GoalMilestoneUiModel(75, "75% Milestone", "LKR 375000.00", "Almost there!", false),
                    GoalMilestoneUiModel(100, "Goal Reached!", "LKR 500000.00", "Congratulations!", false)
                ),
                contributions = listOf(
                    GoalContributionItemUiModel("c1", "LKR 125000.00", "04 Nov 2026", false)
                )
            ),
            onBack = {},
            onRetryLoad = {},
            onAmountChanged = {},
            onAddContribution = {}
        )
    }
}

