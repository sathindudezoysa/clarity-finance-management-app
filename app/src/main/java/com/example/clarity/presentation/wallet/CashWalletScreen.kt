package com.example.clarity.presentation.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CashWalletScreen(
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    viewModel: CashWalletViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CashWalletContent(
        state = state,
        onBack = onBack,
        onAddAccount = onAddAccount,
        onSelectMode = viewModel::selectMode,
        onSelectSourceAccount = viewModel::selectSourceAccount,
        onAmountChange = viewModel::setAmount,
        onSubmit = viewModel::submit
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashWalletContent(
    state: CashWalletState,
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    onSelectMode: (WalletActionMode) -> Unit,
    onSelectSourceAccount: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    var accountMenuExpanded by remember { mutableStateOf(false) }
    val selectedSource = state.sourceAccounts.firstOrNull { it.id == state.selectedSourceAccountId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cash Wallet") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = state.cashAccount?.name ?: "Cash Wallet",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${state.currency} ${state.balance}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.mode == WalletActionMode.TOP_UP,
                    onClick = { onSelectMode(WalletActionMode.TOP_UP) },
                    label = { Text("Top Up") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = state.mode == WalletActionMode.EXPENSE,
                    onClick = { onSelectMode(WalletActionMode.EXPENSE) },
                    label = { Text("Expense") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = state.mode == WalletActionMode.INCOME,
                    onClick = { onSelectMode(WalletActionMode.INCOME) },
                    label = { Text("Income") },
                    modifier = Modifier.weight(1f)
                )
            }

            if (state.mode == WalletActionMode.TOP_UP) {
                if (state.sourceAccounts.isEmpty()) {
                    OutlinedButton(
                        onClick = onAddAccount,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add an Account to Top Up From")
                    }
                } else {
                    ExposedDropdownMenuBox(
                        expanded = accountMenuExpanded,
                        onExpandedChange = { accountMenuExpanded = !accountMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedSource?.displayLabel ?: "Select Source Account",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("From Account") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = accountMenuExpanded,
                            onDismissRequest = { accountMenuExpanded = false }
                        ) {
                            state.sourceAccounts.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text("${account.displayLabel} (${account.currency})") },
                                    onClick = {
                                        onSelectSourceAccount(account.id)
                                        accountMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            val enteredMinor = com.example.clarity.domain.util.MoneyUtils.parseMinor(state.amount, state.currency).getOrNull()
            val availableMinor = com.example.clarity.domain.util.MoneyUtils.parseMinor(state.balance, state.currency).getOrDefault(0L)
            val isAmountInvalid = state.amount.isNotBlank() && (enteredMinor == null || enteredMinor <= 0L)
            val isInsufficientBalance = state.mode == WalletActionMode.EXPENSE &&
                enteredMinor != null && enteredMinor > availableMinor
            val hasValidationError = isAmountInvalid || isInsufficientBalance

            OutlinedTextField(
                value = state.amount,
                onValueChange = onAmountChange,
                label = { Text("Amount (${state.currency})") },
                singleLine = true,
                isError = hasValidationError,
                supportingText = when {
                    isInsufficientBalance -> ({ Text("Insufficient cash wallet balance") })
                    isAmountInvalid -> ({ Text("Enter a valid amount greater than zero") })
                    else -> null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            val canSubmit = !state.isSaving &&
                enteredMinor != null &&
                enteredMinor > 0L &&
                !hasValidationError &&
                (state.mode != WalletActionMode.TOP_UP || selectedSource != null)

            Button(
                onClick = onSubmit,
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth()
            ) {
                val buttonLabel = when (state.mode) {
                    WalletActionMode.TOP_UP -> "Top Up Cash Wallet"
                    WalletActionMode.EXPENSE -> "Save Cash Expense"
                    WalletActionMode.INCOME -> "Save Cash Income"
                }
                Text(if (state.isSaving) "Saving..." else buttonLabel)
            }
        }
    }
}
