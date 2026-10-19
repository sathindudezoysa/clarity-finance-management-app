package com.example.clarity.presentation.transactions.edit

import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionTags
import com.example.clarity.domain.model.Account

data class TransactionEditState(
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val transaction: Transaction? = null,
    val isSaving: Boolean = false,
    val isSuccess: Boolean = false,
    val error: Int? = null,
    val amountError: Int? = null,
    val categoryError: Int? = null,
    val noteError: Int? = null,
    val tags: List<String> = TransactionTags.predefined,
    val accounts: List<Account> = emptyList(),
    val selectedAccountId: String? = null,
    val isAccountsLoading: Boolean = true,
    val accountsError: Int? = null,
    val accountError: Int? = null,
    val currency: String = "LKR",
    val targetCurrency: String = "LKR",
    val preview: com.example.clarity.domain.model.ConversionSnapshot? = null,
    val previewLoading: Boolean = false,
    val previewError: Boolean = false
) {
    val canSaveWithAccount: Boolean get() = transaction?.isCash == true || (!isAccountsLoading && accounts.any { it.id == selectedAccountId })
}
