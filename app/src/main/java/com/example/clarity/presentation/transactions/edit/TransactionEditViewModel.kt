package com.example.clarity.presentation.transactions.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.R
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.usecase.transaction.AddTransactionUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionUseCase
import com.example.clarity.domain.usecase.transaction.UpdateTransactionUseCase
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
    savedStateHandle: SavedStateHandle,
    private val getTransaction: GetTransactionUseCase,
    private val addTransaction: AddTransactionUseCase,
    private val updateTransaction: UpdateTransactionUseCase
) : ViewModel() {
    private val id: String? = savedStateHandle["id"]
    private val _uiState = MutableStateFlow(TransactionEditState(isEditMode = id != null, isLoading = id != null))
    val uiState = _uiState.asStateFlow()

    init {
        if (id != null) viewModelScope.launch {
            getTransaction(id).collect { result ->
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

    fun save(type: TransactionType, amountText: String, category: String, note: String, dateEpochMillis: Long) {
        val state = _uiState.value
        if (state.isSaving || state.isSuccess || state.isLoading) return
        val amount = MoneyUtils.parseMinor(amountText).getOrNull()
        val amountError = when {
            amount == null -> R.string.transactions_amount_invalid
            amount <= 0 -> R.string.transactions_amount_positive
            else -> null
        }
        val categoryError = if (category.trim().length > 100) R.string.transactions_category_long else null
        val noteError = if (note.trim().length > 2000) R.string.transactions_note_long else null
        _uiState.update { it.copy(amountError = amountError, categoryError = categoryError, noteError = noteError, error = null) }
        if (amountError != null || categoryError != null || noteError != null) return
        if (id != null && state.transaction == null) {
            _uiState.update { it.copy(error = R.string.transactions_not_found) }
            return
        }
        val transaction = (state.transaction ?: Transaction(amountMinor = checkNotNull(amount))).copy(
            type = type, amountMinor = checkNotNull(amount), category = category, note = note,
            dateEpochMillis = dateEpochMillis
        )
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = if (id == null) addTransaction(transaction) else updateTransaction(transaction)
            result.onSuccess { _uiState.update { it.copy(isSaving = false, isSuccess = true) } }
                .onFailure { error -> _uiState.update { it.copy(
                    isSaving = false,
                    error = if (error.message == "Please sign in") R.string.transactions_sign_in
                    else R.string.transactions_save_error
                ) } }
        }
    }
}
