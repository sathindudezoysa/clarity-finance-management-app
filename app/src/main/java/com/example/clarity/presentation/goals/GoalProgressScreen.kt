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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
        onAddContribution = viewModel::addContribution
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
    onEditGoal: ((String) -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    var accountMenuExpanded by remember { mutableStateOf(false) }
    val selectedAccount = state.fundingAccounts.firstOrNull { it.id == state.selectedAccountId }
    val canSelectAccount = !state.isSaving && state.fundingAccounts.isNotEmpty()
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
                    if (onEditGoal != null && !currentGoalId.isNullOrBlank() && !state.isLoading && state.fatalError == null) {
                        TextButton(
                            onClick = { onEditGoal(currentGoalId) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Edit",
                                color = colors.primary,
                                fontWeight = FontWeight.SemiBold
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
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        state.formError?.let { errorMsg ->
                            item(key = "progress-form-error") {
                                GoalErrorBanner(
                                    message = errorMsg,
                                    onRetry = onAddContribution
                                )
                            }
                        }

                        item(key = "progress-add-card") {
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
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Add Progress",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (state.goalAccountLabel.isNotBlank()) {
                                        Text(
                                            text = "Transfer to goal account: ${state.goalAccountLabel}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant
                                        )
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
                                            "${selectedAccount.label} — ${selectedAccount.balanceFormatted}"
                                        } else {
                                            ""
                                        }
                                        OutlinedTextField(
                                            value = selectedDisplayValue,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Transfer From Account (${state.currency}) *") },
                                            placeholder = {
                                                Text(
                                                    if (state.fundingAccounts.isEmpty()) {
                                                        "No funded ${state.currency} accounts"
                                                    } else {
                                                        "Select a ${state.currency} account"
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
                                            supportingText = {
                                                val err = state.accountError
                                                if (err != null) {
                                                    Text(err)
                                                } else {
                                                    Text("Only ${state.currency} accounts with available balance are shown")
                                                }
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
                                                    "${accountOption.label} (Default)"
                                                } else {
                                                    accountOption.label
                                                }
                                                DropdownMenuItem(
                                                    text = {
                                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                            Text(
                                                                text = optionTitle,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                            Text(
                                                                text = "Available: ${accountOption.balanceFormatted}",
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
                                        label = { Text("Contribution Amount (${state.currency}) *") },
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
                                                    Text("Available to add: ${state.availableToContributeFormatted}")
                                                }
                                                else -> Text("Select a funded ${state.currency} account above")
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
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.5f)),
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
private fun GoalContributionRowCard(item: GoalContributionItemUiModel) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.5f)),
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
                val subtitle = if (!item.sourceAccountLabel.isNullOrBlank()) {
                    "From ${item.sourceAccountLabel} • ${item.dateFormatted}"
                } else {
                    item.dateFormatted
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            if (item.isSyncing) {
                Text(
                    text = "Syncing",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurfaceVariant
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
