package com.example.clarity.presentation.transactions.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.presentation.transactions.edit.TransactionEditContent
import com.example.clarity.presentation.transactions.edit.TransactionEditScreen
import com.example.clarity.presentation.transactions.edit.TransactionEditState
import com.example.clarity.presentation.transactions.edit.TransactionEditViewModel

@Composable
fun TransactionDetailScreen(
    onBack: () -> Unit,
    onCreateAccount: () -> Unit = {},
    onOpenCreateCategory: (TransactionType) -> Unit = {},
    createdCategoryName: String? = null,
    createdCategoryType: String? = null,
    onConsumedCreatedCategory: () -> Unit = {},
    viewModel: TransactionEditViewModel = hiltViewModel()
) {
    TransactionEditScreen(
        onBack = onBack,
        onCreateAccount = onCreateAccount,
        onOpenCreateCategory = onOpenCreateCategory,
        createdCategoryName = createdCategoryName,
        createdCategoryType = createdCategoryType,
        onConsumedCreatedCategory = onConsumedCreatedCategory,
        viewModel = viewModel
    )
}

@Composable
fun TransactionDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit = {},
    viewModel: TransactionDetailViewModel
) {
    val detailState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(detailState.isDeleted) {
        if (detailState.isDeleted) onBack()
    }

    val mappedAccounts = remember(detailState.transaction?.accountId, detailState.transaction?.currency, detailState.accountName) {
        val tx = detailState.transaction
        val accId = tx?.accountId
        val accName = detailState.accountName ?: if (tx?.isCash == true) "Cash Wallet" else null
        if (accId != null && accName != null) {
            listOf(Account(id = accId, name = accName, bankName = "", currency = tx.currency))
        } else {
            emptyList()
        }
    }

    val editState = TransactionEditState(
        isEditMode = true,
        isLoading = detailState.isLoading,
        transaction = detailState.transaction,
        error = detailState.error,
        accounts = mappedAccounts,
        selectedAccountId = detailState.transaction?.accountId,
        isAccountsLoading = detailState.isLoading,
        currency = detailState.transaction?.currency ?: "LKR",
        targetCurrency = detailState.transaction?.targetCurrency ?: "LKR",
        preview = detailState.transaction?.conversion,
        previewError = detailState.transaction?.pendingConversion == true,
        isDeleteDialogVisible = detailState.isDeleteDialogVisible,
        isDeleting = detailState.isDeleting,
        isDeleted = detailState.isDeleted,
        canDelete = detailState.transaction != null
    )

    TransactionEditContent(
        state = editState,
        onBack = onBack,
        onSave = { _, _, _, _, _, _, _ ->
            detailState.transaction?.id?.let(onEdit)
        },
        onAccountSelected = {},
        onCreateAccount = {},
        onRetryPreview = viewModel::retry,
        onRequestDelete = if (editState.canDelete) viewModel::showDeleteDialog else null,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDelete = viewModel::hideDeleteDialog
    )
}
