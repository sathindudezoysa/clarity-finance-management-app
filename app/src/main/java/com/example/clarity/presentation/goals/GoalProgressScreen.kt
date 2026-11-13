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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Edit
import com.example.clarity.presentation.common.AppIcons
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.presentation.theme.ClarityTheme

@Composable
fun GoalProgressScreen(
    onBack: () -> Unit,
    onEditGoal: ((String) -> Unit)? = null,
    viewModel: GoalProgressViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GoalProgressContent(
        state = state,
        onBack = onBack,
        onEditGoal = onEditGoal,
        onRetryLoad = viewModel::loadAndObserve,
        onAccountSelected = viewModel::onAccountSelected,
        onAmountChanged = viewModel::onAmountChanged,
        onAddContribution = viewModel::addContribution,
        onPayoutAccountSelected = viewModel::onPayoutAccountSelected,
        onRetryPayoutPreview = viewModel::retryPayoutPreview,
        onFinishGoal = viewModel::finishGoal
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalProgressContent(
    state: GoalProgressState,
    onBack: () -> Unit,
    onRetryLoad: () -> Unit,
    onAccountSelected: (String) -> Unit,
    onAmountChanged: (String) -> Unit,
    onAddContribution: () -> Unit,
    onPayoutAccountSelected: (String) -> Unit = {},
    onRetryPayoutPreview: () -> Unit = {},
    onFinishGoal: () -> Unit = {},
    onEditGoal: ((String) -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    var accountMenuExpanded by remember { mutableStateOf(false) }
    val selectedAccount = state.fundingAccounts.firstOrNull { it.id == state.selectedAccountId }
    val canSelectAccount = !state.isSaving && !state.isCompleted && !state.isClosed && state.fundingAccounts.isNotEmpty()
    val topBarTitle = state.name.ifBlank { "Goal Progress" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = topBarTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    val currentGoalId = state.goalId
                    if (onEditGoal != null &&
                        !currentGoalId.isNullOrBlank() &&
                        !state.isLoading &&
                        state.fatalError == null &&
                        !state.isCompleted &&
                        !state.isClosed
                    ) {
                        IconButton(
                            onClick = { onEditGoal(currentGoalId) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit",
                                tint = colors.primary
                            )
                        }
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
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Goals")
                        }
                    }
                }

                else -> {
                    val isOngoing = !state.isCompleted && !state.isClosed
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isOngoing) 18.dp else 10.dp)
                    ) {
                        state.formError?.let { errorMsg ->
                            item(key = "progress-form-error") {
                                GoalErrorBanner(
                                    message = errorMsg,
                                    onRetry = {
                                        if (state.isCompleted && !state.isClosed) {
                                            if (state.payoutPreviewError) {
                                                onRetryPayoutPreview()
                                            } else {
                                                onFinishGoal()
                                            }
                                        } else {
                                            onAddContribution()
                                        }
                                    }
                                )
                            }
                        }

                        item(key = "progress-overview-card") {
                            GoalOverviewSummaryCard(state = state)
                        }

                        when {
                            state.isClosed -> {
                                item(key = "progress-closed-history-card") {
                                    GoalClosedHistoryCard(state = state)
                                }
                            }

                            state.isCompleted -> {
                                item(key = "progress-finish-card") {
                                    GoalFinishLockedCard(
                                        state = state,
                                        onPayoutAccountSelected = onPayoutAccountSelected,
                                        onRetryPayoutPreview = onRetryPayoutPreview,
                                        onFinishGoal = onFinishGoal
                                    )
                                }
                            }

                            else -> {
                                item(key = "progress-add-card") {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(24.dp),
                                        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.25f)),
                                        colors = CardDefaults.cardColors(
                                            containerColor = colors.surface,
                                            contentColor = colors.onSurface
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
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
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(CircleShape)
                                                            .background(colors.secondaryContainer),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = AppIcons.Savings,
                                                            contentDescription = null,
                                                            tint = colors.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = "Contribute",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                if (state.goalAccountLabel.isNotBlank()) {
                                                    Surface(
                                                        shape = RoundedCornerShape(50),
                                                        color = colors.surfaceVariant.copy(alpha = 0.65f)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = AppIcons.Target,
                                                                contentDescription = null,
                                                                tint = colors.primary,
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                            Text(
                                                                text = state.goalAccountLabel,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = colors.onSurfaceVariant,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            ExposedDropdownMenuBox(
                                                expanded = accountMenuExpanded && canSelectAccount,
                                                onExpandedChange = {
                                                    if (canSelectAccount) {
                                                        accountMenuExpanded = it
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                val selectedDisplayValue = if (selectedAccount != null) {
                                                    "${selectedAccount.label} • ${selectedAccount.balanceFormatted}"
                                                } else {
                                                    ""
                                                }
                                                OutlinedTextField(
                                                    value = selectedDisplayValue,
                                                    onValueChange = {},
                                                    readOnly = true,
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = AppIcons.AccountBalance,
                                                            contentDescription = null,
                                                            tint = colors.primary
                                                        )
                                                    },
                                                    label = { Text("Source (${state.currency})") },
                                                    placeholder = {
                                                        Text(
                                                            if (state.fundingAccounts.isEmpty()) {
                                                                "No ${state.currency} accounts"
                                                            } else {
                                                                "Select ${state.currency} account"
                                                            }
                                                        )
                                                    },
                                                    trailingIcon = {
                                                        ExposedDropdownMenuDefaults.TrailingIcon(
                                                            expanded = accountMenuExpanded && canSelectAccount
                                                        )
                                                    },
                                                    enabled = canSelectAccount,
                                                    singleLine = true,
                                                    isError = state.accountError != null,
                                                    supportingText = state.accountError?.let { err ->
                                                        { Text(err) }
                                                    },
                                                    modifier = Modifier
                                                        .menuAnchor()
                                                        .fillMaxWidth()
                                                )
                                                ExposedDropdownMenu(
                                                    expanded = accountMenuExpanded && canSelectAccount,
                                                    onDismissRequest = { accountMenuExpanded = false }
                                                ) {
                                                    state.fundingAccounts.forEach { accountOption ->
                                                        val optionTitle = if (accountOption.isDefault) {
                                                            "${accountOption.label} ★"
                                                        } else {
                                                            accountOption.label
                                                        }
                                                        DropdownMenuItem(
                                                            leadingIcon = {
                                                                Icon(
                                                                    imageVector = AppIcons.AccountBalance,
                                                                    contentDescription = null,
                                                                    tint = colors.primary,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            },
                                                            text = {
                                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                                    Text(
                                                                        text = optionTitle,
                                                                        style = MaterialTheme.typography.bodyMedium,
                                                                        fontWeight = FontWeight.Medium
                                                                    )
                                                                    Text(
                                                                        text = accountOption.balanceFormatted,
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        color = colors.onSurfaceVariant
                                                                    )
                                                                }
                                                            },
                                                            onClick = {
                                                                accountMenuExpanded = false
                                                                onAccountSelected(accountOption.id)
                                                            }
                                                        )
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
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = AppIcons.TrendingUp,
                                                        contentDescription = null,
                                                        tint = colors.primary
                                                    )
                                                },
                                                label = { Text("Amount (${state.currency})") },
                                                prefix = { Text("${state.currency} ") },
                                                singleLine = true,
                                                enabled = !state.isSaving && state.fundingError == null && state.selectedAccountId != null,
                                                isError = state.amountError != null,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                supportingText = {
                                                    val err = state.amountError
                                                    when {
                                                        err != null -> Text(err)
                                                        state.availableToContributeFormatted.isNotEmpty() -> {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = AppIcons.Wallet,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(13.dp)
                                                                )
                                                                Text(state.availableToContributeFormatted)
                                                            }
                                                        }
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
                                                    Icon(
                                                        imageVector = Icons.Default.Add,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Add")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "progress-history-header") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.Transactions,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "History",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (state.contributions.isNotEmpty()) {
                                    Surface(
                                        shape = CircleShape,
                                        color = colors.surfaceVariant,
                                        contentColor = colors.onSurfaceVariant
                                    ) {
                                        Text(
                                            text = state.contributions.size.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (state.contributions.isEmpty()) {
                            item(key = "progress-history-empty") {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = colors.surfaceVariant.copy(alpha = 0.45f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.Lightbulb,
                                            contentDescription = null,
                                            tint = colors.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "No contributions yet",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.onSurfaceVariant
                                        )
                                    }
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
private fun GoalOverviewSummaryCard(state: GoalProgressState) {
    val colors = MaterialTheme.colorScheme
    val isFinishedOrLocked = state.isCompleted || state.isClosed

    if (isFinishedOrLocked) {
        // Compact overview card for completed/closed goals so completed goals take minimal space
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(colors.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Trophy,
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = state.savedFormatted,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Calendar,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = state.targetDateFormatted,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "100%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        return
    }

    // Spacious cognitive overview card for ongoing goals
    val compactPercent = state.percentLabel.removeSuffix(" saved")
    val compactCountdown = state.daysRemainingLabel
        .replace(" days left", "d")
        .replace(" day left", "d")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.25f)),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface,
            contentColor = colors.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Savings,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = state.savedFormatted,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Target,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = state.targetFormatted,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Psychology,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = compactPercent,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                    }
                }
            }

            GoalRoundedProgressBar(
                progress = state.progressFraction,
                trackColor = colors.surfaceVariant,
                indicatorColor = colors.primary,
                height = 12.dp,
                showMilestoneTicks = true
            )

            // Visual 4-step cognitive milestone indicators (25%, 50%, 75%, 100%)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val milestones = listOf(25, 50, 75, 100)
                milestones.forEach { threshold ->
                    val reached = (state.progressFraction * 100f) >= threshold
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (reached) colors.primaryContainer else colors.surfaceVariant.copy(alpha = 0.55f),
                        contentColor = if (reached) colors.onPrimaryContainer else colors.onSurfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (threshold == 100) AppIcons.Trophy else AppIcons.Flag,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "$threshold%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (reached) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Calendar,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = state.targetDateFormatted,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    val countdownColor = if (state.isOverdue) colors.error else colors.onSurfaceVariant
                    Icon(
                        imageVector = AppIcons.Schedule,
                        contentDescription = null,
                        tint = countdownColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = compactCountdown,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = countdownColor
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalFinishLockedCard(
    state: GoalProgressState,
    onPayoutAccountSelected: (String) -> Unit,
    onRetryPayoutPreview: () -> Unit,
    onFinishGoal: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var payoutMenuExpanded by remember { mutableStateOf(false) }
    val selectedPayout = state.payoutAccounts.firstOrNull { it.id == state.selectedPayoutAccountId }
    val canSelectPayout = !state.isFinishing && state.payoutAccounts.isNotEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface,
            contentColor = colors.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(colors.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Payout",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = AppIcons.SwapHoriz,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = state.savedFormatted,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            ExposedDropdownMenuBox(
                expanded = payoutMenuExpanded && canSelectPayout,
                onExpandedChange = {
                    if (canSelectPayout) {
                        payoutMenuExpanded = it
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                val selectedDisplayValue = if (selectedPayout != null) {
                    "${selectedPayout.label} (${selectedPayout.currency}) • ${selectedPayout.balanceFormatted}"
                } else {
                    ""
                }
                OutlinedTextField(
                    value = selectedDisplayValue,
                    onValueChange = {},
                    readOnly = true,
                    leadingIcon = {
                        Icon(
                            imageVector = AppIcons.AccountBalance,
                            contentDescription = null,
                            tint = colors.primary
                        )
                    },
                    label = { Text("Destination Account") },
                    placeholder = {
                        Text(
                            if (state.payoutAccounts.isEmpty()) {
                                "No accounts"
                            } else {
                                "Select account"
                            }
                        )
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            expanded = payoutMenuExpanded && canSelectPayout
                        )
                    },
                    enabled = canSelectPayout,
                    singleLine = true,
                    isError = state.payoutAccountError != null,
                    supportingText = state.payoutAccountError?.let { err ->
                        { Text(err) }
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = payoutMenuExpanded && canSelectPayout,
                    onDismissRequest = { payoutMenuExpanded = false }
                ) {
                    state.payoutAccounts.forEach { accountOption ->
                        val optionTitle = if (accountOption.isDefault) {
                            "${accountOption.label} (${accountOption.currency} ★)"
                        } else {
                            "${accountOption.label} (${accountOption.currency})"
                        }
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = AppIcons.AccountBalance,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = optionTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = accountOption.balanceFormatted,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                payoutMenuExpanded = false
                                onPayoutAccountSelected(accountOption.id)
                            }
                        )
                    }
                }
            }

            if (state.isCrossCurrencyPayout) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.secondaryContainer.copy(alpha = 0.55f),
                    contentColor = colors.onSecondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when {
                            state.payoutPreviewLoading -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "${state.currency} → ${state.selectedPayoutAccountCurrency}",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            state.payoutPreview != null && !state.payoutPreviewFormatted.isNullOrBlank() -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.SwapHoriz,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = state.payoutPreviewFormatted,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "(1 ${state.currency} = ${state.payoutPreview.rate} ${state.selectedPayoutAccountCurrency})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }

                            else -> {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Sync,
                                        contentDescription = null,
                                        tint = colors.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Offline (${state.currency} → ${state.selectedPayoutAccountCurrency})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.error
                                    )
                                }
                                TextButton(onClick = onRetryPayoutPreview) {
                                    Text("Retry", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onFinishGoal,
                enabled = state.isFinishEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
            ) {
                if (state.isFinishing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colors.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Closing...")
                } else {
                    Icon(
                        imageVector = AppIcons.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Finish & Transfer",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalClosedHistoryCard(state: GoalProgressState) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = AppIcons.SwapHoriz,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = state.closedPayoutFormatted ?: state.savedFormatted,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
            }

            if (!state.closedDestinationAccountLabel.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.AccountBalance,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = state.closedDestinationAccountLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!state.closedDateFormatted.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Calendar,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = state.closedDateFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalContributionRowCard(item: GoalContributionItemUiModel) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface,
            contentColor = colors.onSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colors.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.ArrowUpward,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = item.amountFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!item.sourceAccountLabel.isNullOrBlank()) {
                            Icon(
                                imageVector = AppIcons.AccountBalance,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = item.sourceAccountLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        Icon(
                            imageVector = AppIcons.Calendar,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = item.dateFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
            if (item.isSyncing) {
                Icon(
                    imageVector = AppIcons.Sync,
                    contentDescription = "Syncing",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
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
                goalAccountLabel = "Emergency Fund (LKR)",
                savedFormatted = "LKR 250000.00",
                targetFormatted = "LKR 500000.00",
                targetDateFormatted = "15 Jun 2027",
                daysRemainingLabel = "120 days left",
                progressFraction = 0.5f,
                percentLabel = "50% saved",
                fundingAccounts = listOf(
                    GoalFundingAccountUiModel(
                        id = "bank-1",
                        label = "Daily Spending (Commercial Bank)",
                        currency = "LKR",
                        balanceFormatted = "LKR 85000.00",
                        isDefault = true
                    ),
                    GoalFundingAccountUiModel(
                        id = "cash_wallet_1",
                        label = "Cash Wallet (Cash)",
                        currency = "LKR",
                        balanceFormatted = "LKR 12000.00",
                        isDefault = false
                    )
                ),
                selectedAccountId = "bank-1",
                selectedAccountLabel = "Daily Spending (Commercial Bank)",
                availableToContributeFormatted = "LKR 85000.00",
                availabilityLimitLabel = "Available in Daily Spending (Commercial Bank): LKR 85000.00",
                contributions = listOf(
                    GoalContributionItemUiModel(
                        id = "c1",
                        amountFormatted = "LKR 125000.00",
                        dateFormatted = "04 Nov 2026",
                        isSyncing = false,
                        sourceAccountLabel = "Daily Spending (Commercial Bank)"
                    )
                )
            ),
            onBack = {},
            onRetryLoad = {},
            onAccountSelected = {},
            onAmountChanged = {},
            onAddContribution = {},
            onEditGoal = {}
        )
    }
}
