package com.example.clarity.presentation.transactions.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.R
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.util.MoneyUtils
import com.example.clarity.presentation.theme.ClarityTheme
import com.example.clarity.presentation.transactions.formatTransactionDate

@Composable
fun TransactionListScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: TransactionListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TransactionListContent(state, onBack, onAdd, onOpen)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionListContent(
    state: TransactionListState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit
) {
    val addDescription = stringResource(R.string.transactions_add)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transactions_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.transactions_back)) } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, modifier = Modifier.semantics { contentDescription = addDescription }) {
                Text(stringResource(R.string.transactions_add_symbol), style = MaterialTheme.typography.headlineMedium)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                if (state.pendingCount > 0) Text(stringResource(R.string.currency_incomplete_totals, state.pendingCount))
                state.totalsByCurrency.forEach { (currency, totals) ->
                Text(currency)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Total(Modifier.weight(1f), R.string.transactions_income, MoneyUtils.formatMinor(totals.income, currency))
                    Total(Modifier.weight(1f), R.string.transactions_expense, MoneyUtils.formatMinor(totals.expense, currency))
                    Total(Modifier.weight(1f), R.string.transactions_net, MoneyUtils.formatMinor(totals.net, currency))
                }
                }
                if (state.transactions.any { it.conversion?.provider == "ExchangeRate-API" }) com.example.clarity.presentation.transactions.RateAttribution()
                }
            }
            state.error?.let { error -> item { Text(stringResource(error), color = MaterialTheme.colorScheme.error) } }
            if (state.isLoading) item { CircularProgressIndicator() }
            else if (state.transactions.isEmpty()) item {
                Text(stringResource(R.string.transactions_empty), style = MaterialTheme.typography.bodyLarge)
            }
            items(state.transactions, key = { it.id }) { transaction ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(transaction.id) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(transaction.category, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.transactions_account_value,
                            state.accountNames[transaction.accountId] ?: stringResource(
                                if (transaction.accountId == null) R.string.transactions_account_unassigned
                                else R.string.transactions_account_unavailable)), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(
                                if (transaction.type == TransactionType.INCOME) R.string.transactions_income_amount
                                else R.string.transactions_expense_amount,
                                transaction.currency + " " + MoneyUtils.formatMinor(transaction.amountMinor, transaction.currency)
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        com.example.clarity.presentation.transactions.ConversionInfo(transaction.targetCurrency,
                            transaction.conversion, transaction.pendingConversion)
                        if (transaction.note.isNotBlank()) Text(
                            transaction.note, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(formatTransactionDate(transaction.dateEpochMillis), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun Total(modifier: Modifier, label: Int, value: String) {
    Column(modifier) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true)
@Composable
private fun TransactionListPreview() {
    ClarityTheme {
        TransactionListContent(
            TransactionListState(isLoading = false, transactions = listOf(Transaction(amountMinor = 1250))),
            {}, {}, {}
        )
    }
}
