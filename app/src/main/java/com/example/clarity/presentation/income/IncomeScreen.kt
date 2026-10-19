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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            Text(String.format("Monthly income awaiting confirmation: %d", count), style = MaterialTheme.typography.titleMedium)
            Text("Income is recorded only after you confirm receiving it.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onReview, modifier = Modifier.fillMaxWidth()) { Text("Review income") }
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
    Scaffold(topBar = { TopAppBar(title = { Text("Income sources") },
        navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        IncomeContent(state, month, viewModel::edit, viewModel::review, viewModel::stop,
            onOpenTransaction, onAddAccount, Modifier.padding(padding))
    }
    state.draft?.let { draft ->
        AlertDialog(onDismissRequest = viewModel::dismiss, title = { Text("Edit source") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft.name, viewModel::name, label = { Text("Source name") },
                    singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.amount, viewModel::amount, label = { Text("Expected amount (LKR)") },
                    singleLine = true, enabled = !state.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Recurring monthly", modifier = Modifier.weight(1f))
                    val recurringLabel = "Recurring monthly"
                    Switch(draft.recurring, viewModel::recurring, enabled = !state.busy,
                        modifier = Modifier.semantics { contentDescription = recurringLabel })
                }
                Text("Income is recorded only after you confirm receiving it.", style = MaterialTheme.typography.bodySmall)
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }, enabled = !state.busy && state.accounts.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text(state.accounts.firstOrNull { it.id == draft.accountId }?.name ?: "Choose a receiving account")
                    }
                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        state.accounts.forEach { account -> DropdownMenuItem(text = { Text("${account.name} (${account.bankName})") },
                            onClick = { viewModel.account(account.id); expanded = false }) }
                    }
                }
                if (state.accounts.isEmpty()) TextButton(onClick = onAddAccount) { Text("Add an LKR bank account") }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(onClick = viewModel::saveDraft, enabled = !state.busy && draft.accountId.isNotBlank()) { Text("Save source") } },
            dismissButton = { TextButton(onClick = viewModel::dismiss, enabled = !state.busy) { Text("Cancel") } })
    }
    state.confirmation?.let { source ->
        val account = state.accounts.firstOrNull { it.id == source.accountId }?.name ?: "Choose a receiving account"
        AlertDialog(onDismissRequest = viewModel::dismiss, title = { Text("Confirm income received") },
            text = { Text("Record LKR ${MoneyUtils.formatMinor(source.amountMinor)} from ${source.name} into $account for " +
                (state.confirmationMonth?.let { YearMonth.parse(it).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())) } ?: "today") + "?") },
            confirmButton = { TextButton(onClick = viewModel::confirm, enabled = !state.busy) { Text("Confirm receipt") } },
            dismissButton = { TextButton(onClick = viewModel::dismiss, enabled = !state.busy) { Text("Cancel") } })
    }
}

@Composable
fun IncomeContent(state: IncomeState, month: String, onEdit: (IncomeSource?) -> Unit, onReview: (IncomeSource) -> Unit,
    onStop: (String) -> Unit, onOpen: (String) -> Unit, onAddAccount: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.testTag("income-list").fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            Text("Track salary, freelance work and other income separately. Recurring sources ask for confirmation each month.", style = MaterialTheme.typography.bodyMedium)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(onClick = { onEdit(null) }, enabled = !state.busy && !state.loading && state.ownerId != null, modifier = Modifier.fillMaxWidth()) { Text("Add income source") }
            if (state.accounts.isEmpty() && !state.loading) TextButton(onClick = onAddAccount) { Text("Add an LKR bank account") }
            state.lastTransactionId?.let { id -> TextButton(onClick = { onOpen(id) }) { Text("Income recorded; view transaction") } }
            if (!state.loading && state.sources.isEmpty()) Text("No income sources yet. Add your first source to start tracking income.")
        }
        items(state.sources.sortedBy { !it.isDue(state.period) }, key = { it.id }) { source ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(source.name, style = MaterialTheme.typography.titleMedium)
                    Text("Expected: LKR ${MoneyUtils.formatMinor(source.amountMinor)}")
                    Text(state.accounts.firstOrNull { it.id == source.accountId }?.name ?: "Receiving account unavailable; edit source")
                    if (source.pendingSync) Text("Saved locally; sync pending", style = MaterialTheme.typography.bodySmall)
                    if (source.recurring) Text(if (source.isDue(state.period)) "Confirm receipt for $month" else "Confirmed for $month")
                    Button(onClick = { onReview(source) }, enabled = !state.busy && state.accounts.any { it.id == source.accountId } && (!source.recurring || source.isDue(state.period)), modifier = Modifier.fillMaxWidth()) {
                        Text(if (source.recurring) "Confirm this month" else "Record income received")
                    }
                    Row {
                        TextButton(onClick = { onEdit(source) }, enabled = !state.busy) { Text("Edit source") }
                        TextButton(onClick = { onStop(source.id) }, enabled = !state.busy) { Text("Stop tracking") }
                    }
                }
            }
        }
    }
}
