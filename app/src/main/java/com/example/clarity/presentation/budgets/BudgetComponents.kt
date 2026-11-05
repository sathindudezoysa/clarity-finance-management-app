package com.example.clarity.presentation.budgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.clarity.domain.model.BudgetStatus
import com.example.clarity.domain.model.BudgetThreshold
import com.example.clarity.presentation.common.AppIcons
import java.math.BigDecimal
import java.math.RoundingMode

/** Colors for one budget status, aligned with the Goal Card theme palette. */
private class BudgetTone(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
    val iconContainer: Color,
    val iconTint: Color
)

@Composable
private fun budgetTone(threshold: BudgetThreshold, live: Boolean): BudgetTone {
    val colors = MaterialTheme.colorScheme
    return when {
        // Past months are history, not warnings.
        !live -> BudgetTone(
            accent = colors.outline,
            container = colors.surfaceVariant,
            onContainer = colors.onSurfaceVariant,
            iconContainer = colors.surfaceVariant,
            iconTint = colors.onSurfaceVariant
        )
        threshold == BudgetThreshold.NORMAL -> BudgetTone(
            accent = colors.primary,
            container = colors.secondaryContainer,
            onContainer = colors.primary,
            iconContainer = colors.secondaryContainer,
            iconTint = colors.primary
        )
        threshold == BudgetThreshold.NEARING -> BudgetTone(
            accent = colors.tertiary,
            container = colors.tertiaryContainer,
            onContainer = colors.onTertiaryContainer,
            iconContainer = colors.tertiaryContainer,
            iconTint = colors.tertiary
        )
        else -> BudgetTone(
            accent = colors.error,
            container = colors.errorContainer,
            onContainer = colors.onErrorContainer,
            iconContainer = colors.errorContainer,
            iconTint = colors.error
        )
    }
}

internal fun formatBudgetPercentLabel(status: BudgetStatus): String {
    if (status.budget.limitMinor <= 0L) return "0%"
    val percent = BigDecimal(status.spent)
        .multiply(BigDecimal(100))
        .divide(BigDecimal.valueOf(status.budget.limitMinor), 1, RoundingMode.DOWN)
        .stripTrailingZeros()
    return "${percent.toPlainString()}%"
}

@Composable
internal fun BudgetRoundedProgressBar(
    progress: Float,
    trackColor: Color,
    indicatorColor: Color,
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
internal fun BudgetMessageBanner(
    icon: ImageVector,
    message: String,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isError) colors.errorContainer else colors.surfaceVariant,
        contentColor = if (isError) colors.onErrorContainer else colors.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun BudgetEmptyState(onCreate: (() -> Unit)? = null) {
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
                    imageVector = AppIcons.Budgets,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = BudgetUiText.EMPTY_TITLE,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = BudgetUiText.EMPTY,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (onCreate != null) {
                Button(
                    onClick = onCreate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(BudgetUiText.CREATE)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BudgetCard(
    status: BudgetStatus,
    selected: Boolean,
    live: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    enabled: Boolean
) {
    val budget = status.budget
    val warning = live && status.threshold != BudgetThreshold.NORMAL
    val progress = BigDecimal(status.spent)
        .divide(BigDecimal.valueOf(budget.limitMinor), 4, RoundingMode.DOWN)
        .toFloat()
        .coerceIn(0f, 1f)
    val percentLabel = formatBudgetPercentLabel(status)
    val tone = budgetTone(status.threshold, live)
    val colors = MaterialTheme.colorScheme
    val spentFormatted = budgetMoney(status.spent, budget.currency)
    val limitFormatted = budgetMoney(budget.limitMinor.toBigInteger(), budget.currency)
    val remainingFormatted = budgetMoney(status.remaining.abs(), budget.currency)
    val exceeded = status.remaining.signum() < 0
    val statusLabel = budgetStatusText(status.threshold, status.uncounted > 0)

    Card(
        shape = RoundedCornerShape(20.dp),
        border = if (selected) {
            BorderStroke(2.dp, colors.primary)
        } else {
            BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
        },
        colors = CardDefaults.cardColors(
            containerColor = colors.surface,
            contentColor = colors.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onEdit)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header Row: Circular Icon + Category/Period + Percentage Label
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(tone.iconContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Budgets,
                        contentDescription = null,
                        tint = tone.iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = budget.category,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = budgetPeriodSubtitleText(status.activePeriod, budget.period),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = percentLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = tone.accent
                )
            }

            // 2. Spent / Limit Amounts + Status Pill
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            append(spentFormatted)
                        }
                        withStyle(
                            SpanStyle(
                                color = colors.onSurfaceVariant,
                                fontWeight = FontWeight.Normal,
                                fontSize = MaterialTheme.typography.bodySmall.fontSize
                            )
                        ) {
                            append(" / $limitFormatted")
                        }
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                BudgetStatusPill(
                    label = statusLabel,
                    tone = tone,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }

            // 3. Rounded Pill Progress Bar
            BudgetRoundedProgressBar(
                progress = progress,
                trackColor = colors.surfaceVariant,
                indicatorColor = tone.accent,
                modifier = Modifier.semantics {
                    contentDescription = "Budget progress $percentLabel, ${budgetSpendingText(spentFormatted, limitFormatted)}"
                }
            )

            // Optional Warning / Uncounted Currency Callouts
            if (warning) {
                BudgetFeedback(
                    message = budgetFeedbackText(status.threshold, budget.category, budget.period),
                    tone = tone
                )
            }
            if (status.uncounted > 0) {
                BudgetUncounted(
                    uncounted = status.uncounted,
                    onRetry = onRetry,
                    enabled = enabled
                )
            }

            // 4. Subtle Divider
            Divider(color = colors.outlineVariant.copy(alpha = 0.35f))

            // 5. Footer Row: Remaining / Sync Status on Left + Edit / Disable Actions on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (exceeded) {
                            budgetExceededByText(remainingFormatted)
                        } else {
                            budgetRemainingText(remainingFormatted)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (exceeded && live) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (exceeded && live) colors.error else colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (budget.pendingSync) {
                        Text(
                            text = BudgetUiText.PENDING_SYNC,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onEdit,
                        enabled = enabled,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = BudgetUiText.EDIT,
                            color = colors.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    TextButton(
                        onClick = onDelete,
                        enabled = enabled,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = colors.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = BudgetUiText.DELETE,
                            color = colors.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetStatusPill(label: String, tone: BudgetTone, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = tone.container,
        contentColor = tone.onContainer
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BudgetFeedback(message: String, tone: BudgetTone) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = tone.container,
        contentColor = tone.onContainer
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BudgetUncounted(uncounted: Int, onRetry: () -> Unit, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer
    ) {
        Column(Modifier.padding(start = 12.dp, top = 12.dp, end = 4.dp, bottom = 4.dp)) {
            Row(
                modifier = Modifier.padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(budgetIncompleteText(uncounted), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(
                onClick = onRetry,
                enabled = enabled,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = BudgetUiText.RETRY,
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** A compact dashboard row; every shown status belongs to the current month or year. */
@Composable
internal fun BudgetDashboardAlert(status: BudgetStatus, onClick: () -> Unit) {
    val budget = status.budget
    val tone = budgetTone(status.threshold, live = true)
    val colors = MaterialTheme.colorScheme
    val periodText = budgetCategoryMonthText(budget.category, status.activePeriod).substringAfter(" · ")
    val pillLabel = if (status.remaining.signum() < 0) {
        budgetExceededByText(budgetMoney(-status.remaining, budget.currency))
    } else {
        budgetStatusText(status.threshold, status.uncounted > 0)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tone.iconContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (status.threshold.level > 0) Icons.Default.Warning else AppIcons.Budgets,
                contentDescription = null,
                tint = tone.iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = budget.category,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface
                    )
                    Text(
                        text = periodText,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
                BudgetStatusPill(pillLabel, tone, Modifier.weight(1f, fill = false))
            }
            Text(
                text = budgetSpendingText(
                    budgetMoney(status.spent, budget.currency),
                    budgetMoney(budget.limitMinor.toBigInteger(), budget.currency)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            if (status.uncounted > 0) {
                Text(
                    text = budgetIncompleteText(status.uncounted),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
