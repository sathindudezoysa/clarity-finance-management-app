package com.example.clarity.presentation.transactions.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.R
import com.example.clarity.domain.usecase.transaction.DeleteTransactionUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionUseCase
import com.example.clarity.domain.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTransaction: GetTransactionUseCase,
    private val deleteTransaction: DeleteTransactionUseCase
) : ViewModel() {
    private val id: String = checkNotNull(savedStateHandle["id"])
    private val _uiState = MutableStateFlow(TransactionDetailState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getTransaction(id).collect { result ->
                _uiState.update {
                    when (result) {
                        is Resource.Loading -> TransactionDetailState()
                        is Resource.Success -> it.copy(isLoading = false, transaction = result.data, error = null)
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

    fun showDeleteDialog() { _uiState.update { it.copy(isDeleteDialogVisible = true) } }
    fun hideDeleteDialog() { _uiState.update { it.copy(isDeleteDialogVisible = false) } }

    fun confirmDelete() {
        if (_uiState.value.isDeleting || _uiState.value.transaction == null) return
        _uiState.update { it.copy(isDeleting = true, isDeleteDialogVisible = false, error = null) }
        viewModelScope.launch {
            deleteTransaction(id).onSuccess {
                _uiState.update { it.copy(isDeleting = false, isDeleted = true) }
            }.onFailure { error ->
                _uiState.update { it.copy(
                    isDeleting = false,
                    error = if (error.message == "Please sign in") R.string.transactions_sign_in
                    else R.string.transactions_delete_error
                ) }
            }
        }
    }
}
