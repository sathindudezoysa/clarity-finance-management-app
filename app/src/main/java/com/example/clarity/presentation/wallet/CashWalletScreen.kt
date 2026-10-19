package com.example.clarity.presentation.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.domain.model.WalletActivity
import com.example.clarity.presentation.transactions.formatTransactionTimestamp
import java.math.BigInteger

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CashWalletScreen(
    onBack: () -> Unit, onOpenTransaction: (String) -> Unit,
    viewModel: CashWalletViewModel = hiltViewModel(),
    onRegisterBank: () -> Unit = {}, onManageBank: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDetails by rememberSaveable(state.action) { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Cash Wallet") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
    }, actions = {
        IconButton(onClick = { viewModel.selectAction(WalletAction.AUTOMATIC) }, enabled = !state.isSaving) {
            Icon(Icons.Default.Settings, "Top-up Settings")
        }
    }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Available Cash Balance")
                            Text(walletMoney(state.wallet.balanceMinor), style = MaterialTheme.typography.headlineLarge)
                            if (state.wallet.topUpOption?.enabled == true) Text(
                                if (state.wallet.topUpOption?.automaticExecution == true) "Automatic top-ups enabled" else "Low-balance reminder enabled",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (state.isLoading) item { CircularProgressIndicator() }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.showAddCash(true) }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) { Text("Add Cash") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.selectAction(WalletAction.INCOME) }, enabled = !state.isSaving, modifier = Modifier.weight(1f)) { Text("Record Income") }
                            OutlinedButton(onClick = { viewModel.selectAction(WalletAction.EXPENSE) }, enabled = !state.isSaving, modifier = Modifier.weight(1f)) { Text("Record Expense") }
                        }
                    }
                }
                if (state.reminderVisible) item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Your cash wallet is running low.", style = MaterialTheme.typography.titleMedium)
                            val option = state.wallet.topUpOption
                            val bank = state.wallet.accounts.firstOrNull { it.id == option?.bankAccountId }
                            Text("Would you like to record a ${walletMoney(option?.amountMinor ?: 0)} top-up from ${bank?.bankName.orEmpty()}?")
                            Button(onClick = viewModel::reviewReminder, enabled = !state.isSaving) { Text("Review Top-up") }
                            TextButton(onClick = viewModel::dismissReminder) { Text("Not Now") }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val enabled = !state.isSaving && !state.isLoading
                        Text(when (state.action) {
                            WalletAction.EXPENSE -> "Record cash expense"
                            WalletAction.INCOME -> "Receive cash income"
                            WalletAction.TOP_UP -> "Transfer From Bank"
                            WalletAction.ADJUSTMENT -> "Starting Cash / Balance Adjustment"
                            WalletAction.AUTOMATIC -> "Top-up Settings"
                        }, style = MaterialTheme.typography.titleLarge)
                        if (state.action == WalletAction.TOP_UP || state.action == WalletAction.AUTOMATIC) {
                            RegisteredAccountSelector(state, enabled, viewModel::selectBank)
                            if (state.wallet.accounts.none { it.isActive }) TextButton(onClick = onRegisterBank) { Text("Add Bank Account") }
                        }
                        if (state.action == WalletAction.AUTOMATIC) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Enable low-balance top-ups", Modifier.weight(1f))
                                Switch(state.automaticEnabled, viewModel::setAutomatic, enabled = enabled)
                            }
                            Text("Choose reminders or automatic recording, a source account, a minimum cash balance, and a top-up amount.")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Automatically record top-ups", Modifier.weight(1f))
                                Switch(state.automaticExecution, viewModel::setAutomaticExecution, enabled = enabled && state.automaticEnabled)
                            }
                            Text("When enabled, saving a new cash expense below your minimum automatically records one bank withdrawal and adds the configured amount to cash. Both records save together. This does not withdraw real money from your bank. When off, you receive reminders requiring confirmation. Saving these settings does not transfer funds.", style = MaterialTheme.typography.bodySmall)
                            MoneyField(state.threshold, "Minimum wallet balance", enabled, viewModel::setThreshold)
                            MoneyField(state.automaticAmount, "Top-up amount", enabled, viewModel::setAutomaticAmount)
                        } else {
                            if (state.action == WalletAction.ADJUSTMENT) Text("Enter the cash you physically hold now. This corrects your balance without counting as income or expense.")
                            MoneyField(state.amount, if (state.action == WalletAction.ADJUSTMENT) "Cash balance now" else "Amount", enabled, viewModel::setAmount,
                                onDone = if (state.action == WalletAction.TOP_UP) null else viewModel::save)
                            if (state.action != WalletAction.TOP_UP) TextButton(onClick = { showDetails = !showDetails }) {
                                Text(if (showDetails) "Hide optional details" else "Add optional details")
                            }
                            if (showDetails && (state.action == WalletAction.INCOME || state.action == WalletAction.EXPENSE)) {
                                OutlinedTextField(state.category, viewModel::setCategory, label = { Text("Category (optional)") }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
                            }
                            if (showDetails && state.action != WalletAction.TOP_UP) OutlinedTextField(state.note, viewModel::setNote,
                                label = { Text("Note (optional)") }, enabled = enabled, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                        }
                        Feedback(state)
                        Button(onClick = viewModel::save, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.isSaving) "Saving…" else if (state.action == WalletAction.TOP_UP) "Review Transfer" else "Save")
                        }
                        if (state.error != null) TextButton(onClick = viewModel::retryLoading, enabled = enabled) { Text("Reload wallet") }
                    }
                }
                item { Text("Recent Activity", style = MaterialTheme.typography.titleLarge) }
                if (state.wallet.activity.isEmpty()) item { Text("Your wallet has no transactions yet.") }
                items(state.wallet.activity, key = { it.id }) { entry -> ActivityCard(entry, onOpenTransaction,
                    state.wallet.accounts.firstOrNull { it.id == entry.bankAccountId }?.let { "${it.bankName} — ${it.name}" }) }
                item { Text("Bank Accounts", style = MaterialTheme.typography.titleLarge) }
                if (state.wallet.accounts.isEmpty()) item { Text("Register a bank account to record cash withdrawals.") }
                items(state.wallet.accounts, key = { it.id }) { bank ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BankMark(); BankAccountLabel(bank)
                            TextButton(onClick = { onManageBank(bank.id) }) { Text(if (bank.isActive) "Manage account" else "View history") }
                        }
                    }
                }
                item { TextButton(onClick = onRegisterBank) { Text("Add Bank Account") } }
                item {
                    Text("Top-up History", style = MaterialTheme.typography.titleLarge)
                    Text("Transfers keep your combined bank and cash total unchanged. They are excluded from income and expense totals.", style = MaterialTheme.typography.bodySmall)
                }
                if (state.wallet.topUpHistory.isEmpty()) item { Text("No top-ups recorded yet.") }
                items(state.wallet.topUpHistory, key = { "history:${it.id}" }) { entry -> ActivityCard(entry, onOpenTransaction,
                    state.wallet.accounts.firstOrNull { it.id == entry.bankAccountId }?.let { "${it.bankName} — ${it.name}" }) }
                item {
                    Text("Showing the latest 50 activity records and top-ups. Wallet and bank records are saved on this device; cash income and expenses use your existing transaction sync.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (state.showAddCashChoices) AlertDialog(onDismissRequest = { viewModel.showAddCash(false) }, title = { Text("Add Cash") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            TextButton(onClick = { viewModel.selectAction(WalletAction.ADJUSTMENT) }) { Text("Starting Cash / Balance Adjustment") }
            Text("Cash you already hold; does not count as earnings.")
            TextButton(onClick = { viewModel.selectAction(WalletAction.INCOME) }) { Text("Receive Cash Income") }
            Text("Money you earned or received as new income.")
            TextButton(onClick = { viewModel.selectAction(WalletAction.TOP_UP) }) { Text("Transfer From Bank") }
            Text("Record cash you withdrew from a registered account.")
        }
    }, confirmButton = { TextButton(onClick = { viewModel.showAddCash(false) }) { Text("Cancel") } })
    if (state.showTransferReview) {
        val bank = state.wallet.accounts.firstOrNull { it.id == state.selectedBankId }
        val amount = com.example.clarity.domain.util.MoneyUtils.parseMinor(state.amount).getOrDefault(0)
        AlertDialog(onDismissRequest = { if (!state.isSaving) viewModel.cancelReview() }, title = { Text("Review Transfer") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("From: ${bank?.bankName.orEmpty()} — ${bank?.name.orEmpty()}")
                Text("To: Cash Wallet")
                Text("Top-up Amount: ${walletMoney(amount)}")
                Text("Bank Balance After: ${walletMoney((bank?.balanceMinor ?: BigInteger.ZERO) - BigInteger.valueOf(amount))}")
                Text("Wallet Balance After: ${walletMoney(state.wallet.balanceMinor + BigInteger.valueOf(amount))}")
                Text("This records cash you have withdrawn. It does not withdraw money from your bank.")
                Feedback(state)
            }
        }, confirmButton = {
            TextButton(onClick = viewModel::confirmTransfer, enabled = !state.isSaving && !state.isLoading && bank?.isActive == true) { Text("Confirm Top-up") }
        }, dismissButton = { TextButton(onClick = viewModel::cancelReview, enabled = !state.isSaving) { Text("Back") } })
    }
}

@Composable
private fun MoneyField(value: String, label: String, enabled: Boolean, onChange: (String) -> Unit, onDone: (() -> Unit)? = null) {
    OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, enabled = enabled, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }))
}
@Composable
private fun Feedback(state: CashWalletState) {
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
}
@Composable
private fun ActivityCard(entry: WalletActivity, onOpen: (String) -> Unit, sourceAccount: String? = null) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (entry.kind == "ADJUSTMENT") "Balance adjustment" else entry.title,
                style = MaterialTheme.typography.titleMedium)
            sourceAccount?.let { Text("From: $it", style = MaterialTheme.typography.bodySmall) }
            Text("${if (entry.isCredit) "+" else "−"}${walletMoney(entry.amountMinor)}")
            Text(formatTransactionTimestamp(entry.dateEpochMillis), style = MaterialTheme.typography.bodySmall)
            if (entry.transactionId != null) TextButton(onClick = { onOpen(entry.transactionId) }) { Text("View transaction") }
        }
    }
}
@Composable
private fun RegisteredAccountSelector(state: CashWalletState, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val accounts = state.wallet.accounts.filter { it.isActive }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled && accounts.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            val selected = accounts.firstOrNull { it.id == state.selectedBankId }
            Text(selected?.let { "${it.bankName} — ${it.name}" } ?: "Choose a bank account")
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { bank -> DropdownMenuItem(text = {
                Text("${bank.bankName} \u2014 ${bank.name}" + (if (bank.lastFour.isBlank()) "" else " \u2022\u2022\u2022\u2022 ${bank.lastFour}") + "\n${walletMoney(bank.balanceMinor)}")
            }, leadingIcon = { BankMark() },
                onClick = { onSelect(bank.id); expanded = false }) }
        }
    }
}
