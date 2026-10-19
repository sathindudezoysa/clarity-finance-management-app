package com.example.clarity.presentation.income

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.R
import com.example.clarity.domain.model.IncomeSource
import com.example.clarity.domain.util.MoneyUtils
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun ResumeIncome(viewModel: IncomeViewModel) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) viewModel.refreshPeriod() }
        owner.lifecycle.addObserver(observer)
        viewModel.refreshPeriod()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

@Composable
fun IncomeMonthlyPrompt(onReview: () -> Unit, viewModel: IncomeViewModel = hiltViewModel()) {
    ResumeIncome(viewModel)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val count = state.sources.count { it.isDue(state.period) }
    if (count > 0) OutlinedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.income_monthly_prompt, count), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.income_no_auto), style = MaterialTheme.typography.bodySmall)
            Button(onClick = onReview, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.income_review)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeScreen(onBack: () -> Unit, onAddAccount: () -> Unit, onOpenTransaction: (String) -> Unit,
    viewModel: IncomeViewModel = hiltViewModel()) {
    ResumeIncome(viewModel)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val month = state.period.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.income_title)) },
        navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.transactions_back)) } }) }) { padding ->
        IncomeContent(state, month, viewModel::edit, viewModel::review, viewModel::stop,
            onOpenTransaction, onAddAccount, Modifier.padding(padding))
    }
    state.draft?.let { draft ->
        AlertDialog(onDismissRequest = viewModel::dismiss, title = { Text(stringResource(R.string.income_edit)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft.name, viewModel::name, label = { Text(stringResource(R.string.income_name)) },
                    singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.amount, viewModel::amount, label = { Text(stringResource(R.string.income_amount)) },
                    singleLine = true, enabled = !state.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.income_recurring), modifier = Modifier.weight(1f))
                    val recurringLabel = stringResource(R.string.income_recurring)
                    Switch(draft.recurring, viewModel::recurring, enabled = !state.busy,
                        modifier = Modifier.semantics { contentDescription = recurringLabel })
                }
                Text(stringResource(R.string.income_no_auto), style = MaterialTheme.typography.bodySmall)
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }, enabled = !state.busy && state.accounts.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text(state.accounts.firstOrNull { it.id == draft.accountId }?.name ?: stringResource(R.string.income_choose_account))
                    }
                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        state.accounts.forEach { account -> DropdownMenuItem(text = { Text("${account.name} (${account.bankName})") },
                            onClick = { viewModel.account(account.id); expanded = false }) }
                    }
                }
                if (state.accounts.isEmpty()) TextButton(onClick = onAddAccount) { Text(stringResource(R.string.income_add_account)) }
                state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(onClick = viewModel::saveDraft, enabled = !state.busy && draft.accountId.isNotBlank()) { Text(stringResource(R.string.income_save)) } },
            dismissButton = { TextButton(onClick = viewModel::dismiss, enabled = !state.busy) { Text(stringResource(R.string.transactions_cancel)) } })
    }
    state.confirmation?.let { source ->
        val account = state.accounts.firstOrNull { it.id == source.accountId }?.name ?: stringResource(R.string.income_choose_account)
        AlertDialog(onDismissRequest = viewModel::dismiss, title = { Text(stringResource(R.string.income_confirm_title)) },
            text = { Text(stringResource(R.string.income_confirm_body, MoneyUtils.formatMinor(source.amountMinor), source.name, account,
                state.confirmationMonth?.let { YearMonth.parse(it).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())) } ?: stringResource(R.string.income_today))) },
            confirmButton = { TextButton(onClick = viewModel::confirm, enabled = !state.busy) { Text(stringResource(R.string.income_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::dismiss, enabled = !state.busy) { Text(stringResource(R.string.transactions_cancel)) } })
    }
}

@Composable
fun IncomeContent(state: IncomeState, month: String, onEdit: (IncomeSource?) -> Unit, onReview: (IncomeSource) -> Unit,
    onStop: (String) -> Unit, onOpen: (String) -> Unit, onAddAccount: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.testTag("income-list").fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            Text(stringResource(R.string.income_intro), style = MaterialTheme.typography.bodyMedium)
            state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            if (state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(onClick = { onEdit(null) }, enabled = !state.busy && !state.loading && state.ownerId != null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.income_add)) }
            if (state.accounts.isEmpty() && !state.loading) TextButton(onClick = onAddAccount) { Text(stringResource(R.string.income_add_account)) }
            state.lastTransactionId?.let { id -> TextButton(onClick = { onOpen(id) }) { Text(stringResource(R.string.income_recorded)) } }
            if (!state.loading && state.sources.isEmpty()) Text(stringResource(R.string.income_empty))
        }
        items(state.sources.sortedBy { !it.isDue(state.period) }, key = { it.id }) { source ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(source.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.income_expected, MoneyUtils.formatMinor(source.amountMinor)))
                    Text(state.accounts.firstOrNull { it.id == source.accountId }?.name ?: stringResource(R.string.income_account_unavailable))
                    if (source.pendingSync) Text(stringResource(R.string.income_sync_pending), style = MaterialTheme.typography.bodySmall)
                    if (source.recurring) Text(stringResource(if (source.isDue(state.period)) R.string.income_due else R.string.income_confirmed_month, month))
                    Button(onClick = { onReview(source) }, enabled = !state.busy && state.accounts.any { it.id == source.accountId } && (!source.recurring || source.isDue(state.period)), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (source.recurring) R.string.income_confirm_month else R.string.income_record))
                    }
                    Row {
                        TextButton(onClick = { onEdit(source) }, enabled = !state.busy) { Text(stringResource(R.string.income_edit)) }
                        TextButton(onClick = { onStop(source.id) }, enabled = !state.busy) { Text(stringResource(R.string.income_stop)) }
                    }
                }
            }
        }
    }
}
