package com.example.clarity.presentation.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.domain.model.BankCatalog
import com.example.clarity.R
import com.example.clarity.presentation.transactions.formatTransactionTimestamp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BankAccountScreen(onBack: () -> Unit, onSaved: () -> Unit, onChangeBank: (String?) -> Unit, viewModel: BankAccountViewModel = hiltViewModel(), selectedBankId: String? = null) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var archiveConfirmation by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    LaunchedEffect(selectedBankId) { selectedBankId?.let(viewModel::chooseBank) }
    val active = state.account?.isActive != false
    Scaffold(topBar = { TopAppBar(title = { Text(if (viewModel.editingId == null) "Add Bank Account" else "Bank Account") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
    }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BankMark()
                        Text(BankCatalog.find(state.bankId)?.name ?: "Other Bank / Financial Institution", style = MaterialTheme.typography.titleLarge)
                        if (active) TextButton(onClick = { onChangeBank(viewModel.editingId) }, enabled = !state.isSaving) { Text("Change bank") }
                        if (state.isLoading) CircularProgressIndicator()
                        val enabled = active && !state.isSaving && !state.isLoading
                        state.account?.let { BankAccountLabel(it) }
                        OutlinedTextField(state.nickname, viewModel::nickname, label = { Text(stringResource(R.string.wallet_bank_name)) }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(state.lastFour, viewModel::lastFour, label = { Text("Last four digits (optional)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = enabled, modifier = Modifier.fillMaxWidth())
                        Text("Account Type")
                        listOf("SAVINGS" to "Savings", "CURRENT" to "Current", "OTHER" to "Other").forEach { (id, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = state.type == id, onClick = { viewModel.type(id) }, enabled = enabled)
                                Text(label)
                            }
                        }
                        OutlinedTextField(state.opening, viewModel::opening, label = { Text(stringResource(R.string.wallet_opening_balance)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = enabled && viewModel.editingId == null, modifier = Modifier.fillMaxWidth())
                        Text("Currency: LKR")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(state.isDefault, viewModel::default, enabled = enabled)
                            Text("Set as default account")
                        }
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (active) Button(onClick = viewModel::save, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(if (state.isSaving) "Saving…" else "Save Account") }
                        if (viewModel.editingId != null && active) TextButton(onClick = { archiveConfirmation = true }, enabled = enabled) { Text("Archive account") }
                        if (!active) Text("This account is archived. Its balance and history are preserved.")
                        Text("Recent account activity", style = MaterialTheme.typography.titleLarge)
                        if (state.activity.isEmpty()) Text("No cash withdrawals recorded for this account.")
                    }
                }
                items(state.activity, key = { it.id }) { activity ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Withdrawal to Cash Wallet")
                            Text("−${walletMoney(activity.amountMinor)}")
                            Text(formatTransactionTimestamp(activity.dateEpochMillis))
                        }
                    }
                }
                item { Text("Showing up to 50 recent cash withdrawals. Other bank activity is not connected automatically.", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    if (archiveConfirmation) AlertDialog(onDismissRequest = { archiveConfirmation = false }, title = { Text("Archive this account?") },
        text = { Text("Your balance and history will be kept. This account will no longer be available for new top-ups or reminders.") },
        confirmButton = { TextButton(onClick = { archiveConfirmation = false; viewModel.archive() }) { Text("Archive") } },
        dismissButton = { TextButton(onClick = { archiveConfirmation = false }) { Text("Cancel") } })
}
