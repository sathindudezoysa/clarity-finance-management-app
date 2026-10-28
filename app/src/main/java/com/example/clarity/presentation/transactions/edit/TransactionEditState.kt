package com.example.clarity.presentation.transactions.edit

import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionProof
import com.example.clarity.domain.model.TransactionTags
import com.example.clarity.domain.model.Account

data class PendingProofDraft(
    val uri: String,
    val displayName: String,
    val verified: Boolean = false
)

data class TransactionEditState(
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val transaction: Transaction? = null,
    val isSaving: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val amountError: String? = null,
    val categoryError: String? = null,
    val noteError: String? = null,
    val tags: List<String> = TransactionTags.predefined,
    val accounts: List<Account> = emptyList(),
    val selectedAccountId: String? = null,
    val isAccountsLoading: Boolean = true,
    val accountsError: String? = null,
    val accountError: String? = null,
    val currency: String = "LKR",
    val targetCurrency: String = "LKR",
    val preview: com.example.clarity.domain.model.ConversionSnapshot? = null,
    val previewLoading: Boolean = false,
    val previewError: Boolean = false,
    val proofs: List<TransactionProof> = emptyList(),
    val pendingProofs: List<PendingProofDraft> = emptyList(),
    val isProofBusy: Boolean = false,
    val proofError: String? = null
) {
    val canSaveWithAccount: Boolean get() = !isAccountsLoading && accounts.any { it.id == selectedAccountId }
}
