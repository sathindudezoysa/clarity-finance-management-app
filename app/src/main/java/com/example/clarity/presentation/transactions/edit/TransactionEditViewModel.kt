package com.example.clarity.presentation.transactions.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.R
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.usecase.transaction.AddTransactionUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionTagsUseCase
import com.example.clarity.domain.usecase.transaction.UpdateTransactionUseCase
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.util.MoneyUtils
import com.example.clarity.domain.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionEditViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getTransaction: GetTransactionUseCase,
    private val addTransaction: AddTransactionUseCase,
    private val updateTransaction: UpdateTransactionUseCase,
    private val getTransactionTags: GetTransactionTagsUseCase,
    private val getAccounts: GetAccountsUseCase
) : ViewModel() {
    private val id: String? = savedStateHandle["id"]
    private var selectionInitialized = savedStateHandle.get<Boolean>("account_selection_initialized") ?: false
    private val _uiState = MutableStateFlow(TransactionEditState(isEditMode = id != null, isLoading = id != null,
        selectedAccountId = savedStateHandle["selected_account_id"]))
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getAccounts().collect { result ->
                val accounts = if (result is Resource.Loading) _uiState.value.accounts else result.data.orEmpty()
                if (id == null && !selectionInitialized) {
                    accounts.firstOrNull { it.isDefault }?.let { selectAccount(it.id) }
                }
                _uiState.update { it.copy(accounts = accounts, isAccountsLoading = result is Resource.Loading,
                    accountsError = if (result is Resource.Error) {
                        if (result.message == "Please sign in") R.string.transactions_sign_in
                        else R.string.transactions_accounts_load_error
                    } else null) }
            }
        }
        viewModelScope.launch {
            getTransactionTags().collect { tags -> _uiState.update { it.copy(tags = tags) } }
        }
        if (id != null) viewModelScope.launch {
            getTransaction(id).collect { result ->
                if (!selectionInitialized && result.data != null) selectAccount(result.data.accountId)
                _uiState.update {
                    when (result) {
                        is Resource.Loading -> it.copy(isLoading = true, transaction = null, error = null)
                        is Resource.Success -> it.copy(
                            isLoading = false, transaction = result.data,
                            error = if (result.data == null) R.string.transactions_not_found else null
                        )
                        is Resource.Error -> it.copy(
                            isLoading = false, transaction = result.data,
                            error = if (result.message == "Please sign in") R.string.transactions_sign_in
                            else R.string.transactions_load_error
                        )
                    }
                }
            }
        }
    }

    private fun selectAccount(accountId: String?) {
        selectionInitialized = true
        savedStateHandle["account_selection_initialized"] = true
        savedStateHandle["selected_account_id"] = accountId
        _uiState.update { it.copy(selectedAccountId = accountId, accountError = null) }
    }

    fun onAccountSelected(accountId: String) {
        val state = _uiState.value
        if (state.isSaving || state.isSuccess || state.accounts.none { it.id == accountId }) return
        selectAccount(accountId)
    }

    fun save(type: TransactionType, amountText: String, category: String, note: String, dateEpochMillis: Long) {
        val state = _uiState.value
        if (state.isSaving || state.isSuccess || state.isLoading) return
        if (!state.canSaveWithAccount) {
            _uiState.update { it.copy(accountError = if (state.accounts.isEmpty())
                R.string.transactions_account_required else R.string.transactions_account_select) }
            return
        }
        val amount = MoneyUtils.parseMinor(amountText).getOrNull()
        val amountError = when {
            amount == null -> R.string.transactions_amount_invalid
            amount <= 0 -> R.string.transactions_amount_positive
            else -> null
        }
        val categoryError = if (category.trim().length > 100) R.string.transactions_tag_long else null
        val noteError = if (note.trim().length > 2000) R.string.transactions_description_long else null
        _uiState.update { it.copy(amountError = amountError, categoryError = categoryError, noteError = noteError, error = null) }
        if (amountError != null || categoryError != null || noteError != null) return
        if (id != null && state.transaction == null) {
            _uiState.update { it.copy(error = R.string.transactions_not_found) }
            return
        }
        val transaction = (state.transaction ?: Transaction(amountMinor = checkNotNull(amount))).copy(
            type = type, amountMinor = checkNotNull(amount), category = category, note = note,
            dateEpochMillis = dateEpochMillis, accountId = state.selectedAccountId
        )
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = if (id == null) addTransaction(transaction) else updateTransaction(transaction)
            result.onSuccess { _uiState.update { it.copy(isSaving = false, isSuccess = true) } }
                .onFailure { error -> _uiState.update { it.copy(
                    isSaving = false,
                    error = when (error.message) {
                        "Please sign in" -> R.string.transactions_sign_in
                        "Select a bank account" -> R.string.transactions_account_select
                        "Bank account not found" -> R.string.transactions_account_unavailable
                        else -> R.string.transactions_save_error
                    }
                ) } }
        }
    }
}
