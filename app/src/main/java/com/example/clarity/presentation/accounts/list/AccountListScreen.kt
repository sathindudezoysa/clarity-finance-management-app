package com.example.clarity.presentation.accounts.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.clarity.R
import com.example.clarity.presentation.theme.ClarityTheme

@Composable
fun AccountListScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: AccountListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    AccountListContent(state, onBack, onAdd, onEdit, viewModel::makeDefault)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountListContent(
    state: AccountListState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onMakeDefault: (String) -> Unit
) {
    val addDescription = stringResource(R.string.accounts_add)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accounts_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.accounts_back)) } }
            )
        },
        floatingActionButton = {
            if (state.error != R.string.accounts_sign_in) {
                FloatingActionButton(onClick = onAdd, modifier = Modifier.semantics { contentDescription = addDescription }) {
                    Text(stringResource(R.string.accounts_add_symbol), style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.error?.let { error -> item { Text(stringResource(error), color = MaterialTheme.colorScheme.error) } }
                state.actionError?.let { error -> item { Text(stringResource(error), color = MaterialTheme.colorScheme.error) } }
                if (state.isLoading) item { CircularProgressIndicator() }
                else if (state.accounts.isEmpty() && state.error != R.string.accounts_sign_in) item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.accounts_empty), style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = onAdd) { Text(stringResource(R.string.accounts_add)) }
                    }
                }
                items(state.accounts, key = { it.id }) { account ->
                    Card(Modifier.fillMaxWidth().clickable { onEdit(account.id) }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(account.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (account.isDefault) {
                                    Surface(shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                                        Text(stringResource(R.string.accounts_default), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            Text(stringResource(R.string.accounts_bank_value, account.bankName), style = MaterialTheme.typography.bodyMedium)
                            account.accountType?.let { type ->
                                Text(stringResource(R.string.accounts_type_value, type), style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(stringResource(R.string.accounts_currency_value, account.currency), style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onEdit(account.id) }) { Text(stringResource(R.string.accounts_edit)) }
                                if (!account.isDefault) {
                                    TextButton(onClick = { onMakeDefault(account.id) }, enabled = state.changingDefaultId == null) {
                                        Text(stringResource(R.string.accounts_make_default))
                                    }
                                }
                                if (state.changingDefaultId == account.id) CircularProgressIndicator(Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountListPreview() {
    ClarityTheme { AccountListContent(AccountListState(isLoading = false), {}, {}, {}, {}) }
}
