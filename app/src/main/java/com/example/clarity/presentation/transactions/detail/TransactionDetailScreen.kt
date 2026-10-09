package com.example.clarity.presentation.transactions.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.clarity.R
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.util.MoneyUtils
import com.example.clarity.presentation.transactions.formatTransactionDate
import com.example.clarity.presentation.transactions.formatTransactionTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(state.isDeleted) { if (state.isDeleted) onBack() }

    if (state.isDeleteDialogVisible) AlertDialog(
        onDismissRequest = viewModel::hideDeleteDialog,
        title = { Text(stringResource(R.string.transactions_delete_title)) },
        text = { Text(stringResource(R.string.transactions_delete_confirmation)) },
        confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text(stringResource(R.string.transactions_delete)) } },
        dismissButton = { TextButton(onClick = viewModel::hideDeleteDialog) { Text(stringResource(R.string.transactions_cancel)) } }
    )

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.transactions_detail_title)) },
            navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.transactions_back)) } }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            if (state.isLoading) CircularProgressIndicator()
            else {
                val transaction = state.transaction
                if (transaction == null) Text(stringResource(R.string.transactions_not_found))
                else {
                    Field(R.string.transactions_type, stringResource(
                        if (transaction.type == TransactionType.INCOME) R.string.transactions_income else R.string.transactions_expense
                    ))
                    Field(R.string.transactions_amount, MoneyUtils.formatMinor(transaction.amountMinor))
                    Field(R.string.transactions_category, transaction.category)
                    Field(R.string.transactions_note, transaction.note.ifBlank { stringResource(R.string.transactions_none) })
                    Field(R.string.transactions_date, formatTransactionDate(transaction.dateEpochMillis))
                    Field(R.string.transactions_created, formatTransactionTimestamp(transaction.createdAt))
                    Field(R.string.transactions_updated, formatTransactionTimestamp(transaction.updatedAt))
                    Field(R.string.transactions_id, transaction.id)
                    Field(R.string.transactions_owner, transaction.userId)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onEdit(transaction.id) }, enabled = !state.isDeleting) {
                            Text(stringResource(R.string.transactions_edit))
                        }
                        OutlinedButton(onClick = viewModel::showDeleteDialog, enabled = !state.isDeleting) {
                            Text(stringResource(R.string.transactions_delete))
                        }
                    }
                }
            }
            if (state.isDeleting) CircularProgressIndicator()
        }
    }
}

@Composable
private fun Field(label: Int, value: String) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
