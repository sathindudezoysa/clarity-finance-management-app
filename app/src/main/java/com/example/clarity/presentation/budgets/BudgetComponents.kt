package com.example.clarity.presentation.budgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.clarity.domain.model.*
import com.example.clarity.presentation.theme.WarningAmber

@Composable
fun budgetThresholdColor(threshold: BudgetThreshold): Color = when (threshold) {
    BudgetThreshold.NORMAL -> MaterialTheme.colorScheme.tertiary
    BudgetThreshold.NEARING, BudgetThreshold.REACHED -> WarningAmber
    BudgetThreshold.EXCEEDED -> MaterialTheme.colorScheme.error
}

@Composable
fun BudgetMonthSelector(
    selectedMonth: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onPrevious) {
                Text("‹ Prev")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = budgetMonthLabel(selectedMonth),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = selectedMonth,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onNext) {
                Text("Next ›")
            }
        }
    }
}

@Composable
fun BudgetSummaryCard(
    summary: BudgetCurrencySummary,
    modifier: Modifier = Modifier
) {
    val exceeded = summary.remaining.signum() < 0
    val indicatorColor = when {
        exceeded -> MaterialTheme.colorScheme.error
        summary.incomplete -> WarningAmber
        else -> MaterialTheme.colorScheme.tertiary
    }
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = budgetSummaryTitleText(summary.currency, summary.incomplete),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (summary.incomplete) "${summary.percentText} (incomplete)" else summary.percentText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = indicatorColor
                )
            }
            LinearProgressIndicator(
                progress = summary.progress,
                color = indicatorColor,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Total budget: ${budgetMoney(summary.totalLimit, summary.currency)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = if (summary.incomplete) {
                    "Counted spent so far (incomplete): ${budgetMoney(summary.totalSpent, summary.currency)}"
                } else {
                    "Total spent: ${budgetMoney(summary.totalSpent, summary.currency)}"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            val absRemaining = budgetMoney(summary.remaining.abs(), summary.currency)
            Text(
                text = when {
                    exceeded -> budgetExceededByText(absRemaining)
                    summary.incomplete -> "Remaining from counted spending (incomplete): $absRemaining"
                    else -> "Remaining: $absRemaining"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (exceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            if (summary.incomplete) {
                Text(
                    text = budgetIncompleteText(summary.uncounted),
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningAmber
                )
            }
        }
    }
}

@Composable
fun BudgetCard(
    status: BudgetStatus,
    selected: Boolean,
    live: Boolean,
    onEdit: () -> Unit,
    onDisable: () -> Unit,
    onRetry: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val budget = status.budget
    val warning = live && status.threshold != BudgetThreshold.NORMAL
    val statusColor = budgetThresholdColor(status.threshold)
    Card(
        modifier = modifier.fillMaxWidth(),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (warning || selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${budgetCategoryIcon(budget.category)} ${budgetCategoryMonthText(budget.category, budget.month)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = status.percentText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
            Text(
                text = budgetSpendingText(
                    budgetMoney(status.spent, budget.currency),
                    budgetMoney(budget.limitMinor.toBigInteger(), budget.currency)
                )
            )
            LinearProgressIndicator(
                progress = status.progress,
                color = statusColor,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = budgetStatusBadgeText(status.threshold, status.uncounted > 0),
                color = statusColor,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = budgetMoney(status.remaining.abs(), budget.currency).let { amount ->
                    if (status.remaining.signum() < 0) budgetExceededByText(amount) else budgetRemainingText(amount)
                },
                color = if (status.remaining.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            if (warning) {
                Text(budgetFeedbackText(status.threshold, budget.category))
            }
            if (status.uncounted > 0) {
                Text(budgetIncompleteText(status.uncounted))
                TextButton(onClick = onRetry, enabled = enabled) {
                    Text("Resolve uncounted currencies")
                }
            }
            if (budget.pendingSync) {
                Text(
                    text = "Saved on this device · Waiting to sync",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit, enabled = enabled) {
                    Text("Edit budget")
                }
                TextButton(onClick = onDisable, enabled = enabled) {
                    Text("Disable")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BudgetCategorySelector(
    value: String,
    tags: List<String>,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    error: String?,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val trimmed = value.trim()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Category", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                FilterChip(
                    selected = trimmed.isNotEmpty() && trimmed.equals(tag, ignoreCase = true),
                    onClick = {
                        onValueChange(tag)
                        focusManager.clearFocus()
                    },
                    label = { Text("${budgetCategoryIcon(tag)} $tag") },
                    enabled = enabled
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Category or custom tag") },
            placeholder = { Text("Select a chip or type a category") },
            supportingText = {
                Text(error ?: "Select a category above or enter a custom category name.")
            },
            isError = error != null,
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun BudgetMonthStepperField(
    month: String,
    onMonthChange: (String) -> Unit,
    enabled: Boolean,
    error: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Month", style = MaterialTheme.typography.labelLarge)
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onMonthChange(BudgetSummary.shiftMonth(month, -1)) },
                    enabled = enabled
                ) {
                    Text("‹")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = budgetMonthLabel(month),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = month,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = { onMonthChange(BudgetSummary.shiftMonth(month, 1)) },
                    enabled = enabled
                ) {
                    Text("›")
                }
            }
        }
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun DisableBudgetDialog(
    budget: Budget,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(BudgetMessages.DISABLE_TITLE) },
        text = { Text(budgetDisableConfirmText(budget.category, budget.month)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text("Disable", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cancel")
            }
        }
    )
}

