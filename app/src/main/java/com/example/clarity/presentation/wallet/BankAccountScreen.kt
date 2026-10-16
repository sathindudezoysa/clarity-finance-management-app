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
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(if (viewModel.editingId == null) R.string.wallet_add_bank else R.string.bank_account_title)) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.wallet_back)) }
    }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BankMark()
                        Text(BankCatalog.find(state.bankId)?.name ?: stringResource(R.string.bank_account_other_bank), style = MaterialTheme.typography.titleLarge)
                        if (active) TextButton(onClick = { onChangeBank(viewModel.editingId) }, enabled = !state.isSaving) { Text(stringResource(R.string.bank_account_change_bank)) }
                        if (state.isLoading) CircularProgressIndicator()
                        val enabled = active && !state.isSaving && !state.isLoading
                        state.account?.let { BankAccountLabel(it) }
                        OutlinedTextField(state.nickname, viewModel::nickname, label = { Text(stringResource(R.string.wallet_bank_name)) }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(state.lastFour, viewModel::lastFour, label = { Text(stringResource(R.string.bank_account_last_four)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = enabled, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.bank_account_type))
                        listOf("SAVINGS" to R.string.bank_account_savings, "CURRENT" to R.string.bank_account_current, "OTHER" to R.string.bank_account_other).forEach { (id, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = state.type == id, onClick = { viewModel.type(id) }, enabled = enabled)
                                Text(stringResource(label))
                            }
                        }
                        OutlinedTextField(state.opening, viewModel::opening, label = { Text(stringResource(R.string.wallet_opening_balance)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = enabled,
                            readOnly = viewModel.editingId != null, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.bank_account_currency))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(state.isDefault, viewModel::default, enabled = enabled)
                            Text(stringResource(R.string.bank_account_set_default))
                        }
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (active) Button(onClick = viewModel::save, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (state.isSaving) R.string.wallet_saving else R.string.bank_account_save)) }
                        if (viewModel.editingId != null && active) TextButton(onClick = { archiveConfirmation = true }, enabled = enabled) { Text(stringResource(R.string.bank_account_archive_action)) }
                        if (!active) Text(stringResource(R.string.bank_account_archived))
                        Text(stringResource(R.string.bank_account_recent_activity), style = MaterialTheme.typography.titleLarge)
                        if (state.activity.isEmpty()) Text(stringResource(R.string.bank_account_empty_activity))
                    }
                }
                items(state.activity, key = { it.id }) { activity ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.bank_account_withdrawal))
                            Text("−${walletMoney(activity.amountMinor)}")
                            Text(formatTransactionTimestamp(activity.dateEpochMillis))
                        }
                    }
                }
                item { Text(stringResource(R.string.bank_account_history_hint), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    if (archiveConfirmation) AlertDialog(onDismissRequest = { archiveConfirmation = false }, title = { Text(stringResource(R.string.bank_account_archive_title)) },
        text = { Text(stringResource(R.string.bank_account_archive_message)) },
        confirmButton = { TextButton(onClick = { archiveConfirmation = false; viewModel.archive() }) { Text(stringResource(R.string.bank_account_archive_confirm)) } },
        dismissButton = { TextButton(onClick = { archiveConfirmation = false }) { Text(stringResource(R.string.wallet_cancel)) } })
}
