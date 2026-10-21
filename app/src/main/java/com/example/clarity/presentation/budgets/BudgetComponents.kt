package com.example.clarity.presentation.budgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.clarity.domain.model.BudgetStatus
import com.example.clarity.domain.model.BudgetThreshold
import java.math.BigDecimal
import java.math.RoundingMode

/** Colors for one budget status. The status text label is always shown next to these colors. */
private class BudgetTone(val accent: Color, val container: Color, val onContainer: Color)

@Composable
private fun budgetTone(threshold: BudgetThreshold, live: Boolean): BudgetTone {
    val colors = MaterialTheme.colorScheme
    return when {
        // Past months are history, not warnings.
        !live -> BudgetTone(colors.outline, colors.surfaceVariant, colors.onSurfaceVariant)
        threshold == BudgetThreshold.NORMAL -> BudgetTone(colors.primary, colors.primaryContainer, colors.onPrimaryContainer)
        threshold == BudgetThreshold.NEARING -> BudgetTone(colors.tertiary, colors.tertiaryContainer, colors.onTertiaryContainer)
        else -> BudgetTone(colors.error, colors.errorContainer, colors.onErrorContainer)
    }
}

@Composable
internal fun BudgetMessageBanner(icon: ImageVector, message: String, isError: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isError) colors.errorContainer else colors.surfaceVariant,
        contentColor = if (isError) colors.onErrorContainer else colors.onSurfaceVariant
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun BudgetEmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.padding(16.dp).size(28.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(BudgetUiText.EMPTY_TITLE, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(BudgetUiText.EMPTY, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun BudgetSectionLabel(text: String) {
    Text(text, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun BudgetCard(status: BudgetStatus, selected: Boolean, live: Boolean, onEdit: () -> Unit,
    onDisable: () -> Unit, onRetry: () -> Unit, enabled: Boolean) {
    val budget = status.budget
    val warning = live && status.threshold != BudgetThreshold.NORMAL
    val progress = BigDecimal(status.spent).divide(BigDecimal.valueOf(budget.limitMinor), 4, RoundingMode.DOWN).toFloat().coerceIn(0f, 1f)
    val tone = budgetTone(status.threshold, live)
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (warning) tone.container.copy(alpha = 0.35f).compositeOver(colors.surface) else colors.surface,
            contentColor = colors.onSurface
        ),
        // The selection border is independent of the warning tint.
        border = if (selected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BudgetCardHeader(budget.category, budgetPeriodSubtitleText(budget.month, budget.period),
                budgetStatusText(status.threshold, status.uncounted > 0), tone)
            BudgetSpending(status, progress, tone, live)
            if (warning) BudgetFeedback(budgetFeedbackText(status.threshold, budget.category, budget.period), tone)
            if (status.uncounted > 0) BudgetUncounted(status.uncounted, onRetry, enabled)
            if (budget.pendingSync) Text(BudgetUiText.PENDING_SYNC, style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit, enabled = enabled) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(BudgetUiText.EDIT)
                }
                TextButton(onClick = onDisable, enabled = enabled) { Text(BudgetUiText.DISABLE) }
            }
        }
    }
}

@Composable
private fun BudgetCardHeader(category: String, month: String, statusLabel: String, tone: BudgetTone) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(category, style = MaterialTheme.typography.titleMedium)
            Text(month, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BudgetStatusPill(statusLabel, tone, Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun BudgetStatusPill(label: String, tone: BudgetTone, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = tone.container, contentColor = tone.onContainer) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun BudgetSpending(status: BudgetStatus, progress: Float, tone: BudgetTone, live: Boolean) {
    val budget = status.budget
    val colors = MaterialTheme.colorScheme
    val exceeded = status.remaining.signum() < 0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(budgetSpendingText(budgetMoney(status.spent, budget.currency), budgetMoney(budget.limitMinor.toBigInteger(), budget.currency)),
            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        LinearProgressIndicator(progress = progress,
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = tone.accent, trackColor = colors.surfaceVariant)
        Text(budgetMoney(status.remaining.abs(), budget.currency).let { amount ->
            if (exceeded) budgetExceededByText(amount) else budgetRemainingText(amount) },
            style = MaterialTheme.typography.bodyMedium,
            color = if (exceeded && live) colors.error else colors.onSurfaceVariant)
    }
}

@Composable
private fun BudgetFeedback(message: String, tone: BudgetTone) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = tone.container, contentColor = tone.onContainer) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BudgetUncounted(uncounted: Int, onRetry: () -> Unit, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer) {
        Column(Modifier.padding(start = 12.dp, top = 12.dp, end = 4.dp, bottom = 4.dp)) {
            Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(budgetIncompleteText(uncounted), style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onRetry, enabled = enabled, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(BudgetUiText.RETRY)
            }
        }
    }
}

@Composable
internal fun DisabledBudgetRow(status: BudgetStatus, onClick: () -> Unit, enabled: Boolean) {
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.6f).clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(budgetCategoryMonthText(status.budget.category, status.budget.month), style = MaterialTheme.typography.bodyLarge)
                Text(BudgetUiText.DISABLED_HINT, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/** A compact dashboard row; every shown status belongs to the current month or year. */
@Composable
internal fun BudgetDashboardAlert(status: BudgetStatus, onClick: () -> Unit) {
    val budget = status.budget
    val tone = budgetTone(status.threshold, live = true)
    val colors = MaterialTheme.colorScheme
    val periodText = budgetCategoryMonthText(budget.category, budget.month).substringAfter(" · ")
    val pillLabel = if (status.remaining.signum() < 0) {
        budgetExceededByText(budgetMoney(-status.remaining, budget.currency))
    } else {
        budgetStatusText(status.threshold, status.uncounted > 0)
    }
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = tone.container,
            contentColor = tone.accent
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (status.threshold.level > 0) Icons.Default.Warning else Icons.Default.Info,
                    contentDescription = null,
                    tint = tone.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(budget.category, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(periodText, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
                BudgetStatusPill(pillLabel, tone, Modifier.weight(1f, fill = false))
            }
            Text(
                budgetSpendingText(budgetMoney(status.spent, budget.currency), budgetMoney(budget.limitMinor.toBigInteger(), budget.currency)),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            if (status.uncounted > 0) {
                Text(
                    budgetIncompleteText(status.uncounted),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
