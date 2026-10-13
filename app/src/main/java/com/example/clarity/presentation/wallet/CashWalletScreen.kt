package com.example.clarity.presentation.wallet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.clarity.R
import com.example.clarity.domain.util.MoneyUtils
import com.example.clarity.presentation.transactions.formatTransactionTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashWalletScreen(onBack: () -> Unit, onOpenTransaction: (String) -> Unit, viewModel: CashWalletViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.wallet_title)) }, navigationIcon = {
            TextButton(onClick = onBack) { Text(stringResource(R.string.wallet_back)) }
        })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        LazyColumn(modifier = Modifier.widthIn(max = 840.dp).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.wallet_available_balance))
                        Text(MoneyUtils.formatMinor(state.wallet.balanceMinor), style = MaterialTheme.typography.headlineLarge)
                        if (state.wallet.topUpOption?.enabled == true) Text(stringResource(R.string.wallet_automatic_active))
                    }
                }
            }
            if (state.isLoading) item { CircularProgressIndicator() }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionChip(R.string.wallet_cash_expense, WalletAction.EXPENSE, state, viewModel::selectAction)
                        ActionChip(R.string.wallet_cash_income, WalletAction.INCOME, state, viewModel::selectAction)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionChip(R.string.wallet_top_up, WalletAction.TOP_UP, state, viewModel::selectAction)
                        ActionChip(R.string.wallet_automatic, WalletAction.AUTOMATIC, state, viewModel::selectAction)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val enabled = !state.isSaving && !state.isLoading
                    if (state.action == WalletAction.TOP_UP || state.action == WalletAction.AUTOMATIC) {
                        BankSelector(state, enabled, viewModel::selectBank)
                        Text(stringResource(R.string.wallet_transfer_explanation), style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.action == WalletAction.AUTOMATIC) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(R.string.wallet_enable_automatic), Modifier.weight(1f))
                            Switch(checked = state.automaticEnabled, onCheckedChange = viewModel::setAutomatic, enabled = enabled)
                        }
                        Text(stringResource(R.string.wallet_automatic_explanation), style = MaterialTheme.typography.bodyMedium)
                        MoneyField(state.threshold, R.string.wallet_threshold, enabled, viewModel::setThreshold)
                        MoneyField(state.automaticAmount, R.string.wallet_top_up_amount, enabled, viewModel::setAutomaticAmount, viewModel::save)
                    } else {
                        MoneyField(state.amount, R.string.wallet_amount, enabled, viewModel::setAmount, viewModel::save)
                        if (state.action != WalletAction.TOP_UP) {
                            OutlinedTextField(state.category, viewModel::setCategory, modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.wallet_category_optional)) }, enabled = enabled, singleLine = true)
                            OutlinedTextField(state.note, viewModel::setNote, modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.wallet_note_optional)) }, enabled = enabled, maxLines = 3)
                        }
                    }
                    if (!state.showBankForm) WalletFeedback(state)
                    Button(onClick = viewModel::save, enabled = enabled &&
                        ((state.action != WalletAction.TOP_UP && state.action != WalletAction.AUTOMATIC) || state.selectedBankId.isNotBlank()),
                        modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (state.isSaving) R.string.wallet_saving else R.string.wallet_save))
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.wallet_bank_balances), style = MaterialTheme.typography.titleMedium)
                    state.wallet.accounts.forEach { bank ->
                        Text("${bank.name}: ${MoneyUtils.formatMinor(bank.balanceMinor)}")
                    }
                    TextButton(onClick = { viewModel.showBankForm(true) }, enabled = !state.isSaving && !state.isLoading) {
                        Text(stringResource(R.string.wallet_add_bank))
                    }
                }
            }
            item { Text(stringResource(R.string.wallet_activity), style = MaterialTheme.typography.titleLarge) }
            if (state.wallet.activity.isEmpty()) item { Text(stringResource(R.string.wallet_no_activity)) }
            items(state.wallet.activity, key = { it.id }) { entry ->
                OutlinedCard(Modifier.fillMaxWidth().then(if (entry.transactionId != null)
                    Modifier.clickable { onOpenTransaction(entry.transactionId) } else Modifier)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium)
                        Text("${if (entry.isCredit) "+" else "−"}${MoneyUtils.formatMinor(entry.amountMinor)}")
                        Text(formatTransactionTimestamp(entry.dateEpochMillis), style = MaterialTheme.typography.bodySmall)
                        if (entry.transactionId == null) Text(stringResource(R.string.wallet_withdrawal_record), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        }
    }
    if (state.showBankForm) AlertDialog(onDismissRequest = { if (!state.isSaving) viewModel.showBankForm(false) },
        title = { Text(stringResource(R.string.wallet_add_bank)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(state.bankName, viewModel::setBankName, label = { Text(stringResource(R.string.wallet_bank_name)) },
                    enabled = !state.isSaving, singleLine = true)
                MoneyField(state.bankOpeningBalance, R.string.wallet_opening_balance, !state.isSaving, viewModel::setBankBalance)
                WalletFeedback(state)
            }
        }, confirmButton = {
            TextButton(onClick = viewModel::saveBank, enabled = !state.isSaving) { Text(stringResource(R.string.wallet_save)) }
        }, dismissButton = {
            TextButton(onClick = { viewModel.showBankForm(false) }, enabled = !state.isSaving) { Text(stringResource(R.string.wallet_cancel)) }
        })
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ActionChip(label: Int, action: WalletAction, state: CashWalletState, onSelect: (WalletAction) -> Unit) {
    FilterChip(selected = state.action == action, onClick = { onSelect(action) }, enabled = !state.isSaving,
        label = { Text(stringResource(label)) })
}

@Composable
private fun MoneyField(value: String, label: Int, enabled: Boolean, onChange: (String) -> Unit, onDone: (() -> Unit)? = null) {
    OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(label)) },
        enabled = enabled, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }))
}

@Composable
private fun WalletFeedback(state: CashWalletState) {
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
}

@Composable
private fun BankSelector(state: CashWalletState, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled && state.wallet.accounts.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(state.wallet.accounts.firstOrNull { it.id == state.selectedBankId }?.name ?: stringResource(R.string.wallet_select_bank))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.wallet.accounts.forEach { bank ->
                DropdownMenuItem(text = { Text(bank.name) }, onClick = { onSelect(bank.id); expanded = false })
            }
        }
    }
}
