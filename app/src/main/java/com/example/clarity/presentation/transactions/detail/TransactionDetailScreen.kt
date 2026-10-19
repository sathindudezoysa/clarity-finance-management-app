package com.example.clarity.presentation.transactions.detail

import com.example.clarity.presentation.transactions.proofs.TransactionProofSection
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.presentation.transactions.TransactionMessages
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
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isDeleted) { if (state.isDeleted) onBack() }

    if (state.isDeleteDialogVisible) AlertDialog(
        onDismissRequest = viewModel::hideDeleteDialog,
        title = { Text("Delete transaction?") },
        text = { Text("This transaction will be removed. This cannot be undone.") },
        confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text("Delete") } },
        dismissButton = { TextButton(onClick = viewModel::hideDeleteDialog) { Text("Cancel") } }
    )

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Transaction details") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.isLoading) CircularProgressIndicator()
            else {
                val transaction = state.transaction
                if (transaction == null) {
                    if (state.error == null) Text("Transaction not found.")
                }
                else {
                    Field("Type", if (transaction.type == TransactionType.INCOME) "Income" else "Expense")
                    Field("Amount", transaction.currency + " " + MoneyUtils.formatMinor(transaction.amountMinor, transaction.currency))
                    com.example.clarity.presentation.transactions.ConversionInfo(transaction.targetCurrency,
                        transaction.conversion, transaction.pendingConversion)
                    if (transaction.pendingConversion) {
                        Text("Daily reference rates. A missing rate does not prevent saving offline. Amounts that overflow or round to zero require editing the original amount.")
                        TextButton(onClick = viewModel::retry) { Text("Retry conversion") }
                    }
                    Field("Bank account", if (transaction.isCash) "Cash Wallet" else state.accountName ?: if (transaction.accountId == null)
                        TransactionMessages.ACCOUNT_UNASSIGNED else TransactionMessages.ACCOUNT_UNAVAILABLE)
                    Field("Tag", transaction.category)
                    Field("Description (optional)", transaction.note.ifBlank { "None" })
                    Field("Date", formatTransactionDate(transaction.dateEpochMillis))
                    Field("Created", formatTransactionTimestamp(transaction.createdAt))
                    Field("Updated", formatTransactionTimestamp(transaction.updatedAt))
                    Field("Transaction ID", transaction.id)
                    Field("User ID", transaction.userId)
                    TransactionProofSection(transaction.amountMinor, !state.isDeleting)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onEdit(transaction.id) }, enabled = !state.isDeleting) {
                            Text("Edit")
                        }
                        OutlinedButton(onClick = viewModel::showDeleteDialog, enabled = !state.isDeleting) {
                            Text("Delete")
                        }
                    }
                }
            }
            if (state.isDeleting) CircularProgressIndicator()
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
