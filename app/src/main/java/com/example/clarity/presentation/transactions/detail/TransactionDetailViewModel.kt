package com.example.clarity.presentation.transactions.detail

import com.example.clarity.presentation.navigation.transactionIdArgument
import com.example.clarity.presentation.navigation.TRANSACTION_ID_ARGUMENT
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.TransactionError
import com.example.clarity.domain.model.TransactionException
import com.example.clarity.presentation.transactions.TransactionMessages
import com.example.clarity.domain.usecase.transaction.DeleteTransactionUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionUseCase
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.util.Resource
import com.example.clarity.domain.util.isSignInRequired
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTransaction: GetTransactionUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val getAccounts: GetAccountsUseCase,
    private val retryConversion: com.example.clarity.domain.usecase.transaction.RetryConversionUseCase
) : ViewModel() {
    // get<String> inserts a throwing cast for malformed saved/navigation state; check the runtime type first.
    private val id = savedStateHandle.transactionIdArgument()
    private val _uiState = MutableStateFlow(TransactionDetailState(
        isLoading = id != null,
        error = if (id == null) TransactionMessages.NOT_FOUND else null
    ))
    val uiState = _uiState.asStateFlow()

    init {
        if (id != null) viewModelScope.launch {
            combine(getTransaction(id), getAccounts()) { transaction, accounts -> transaction to accounts }.collect { (result, accounts) ->
                _uiState.update {
                    val transaction = result.data ?: it.transaction.takeIf {
                        result is Resource.Loading || (result is Resource.Error && !result.cause.isSignInRequired())
                    }
                    val accountName = if (transaction == null) null else if (accounts.data == null) it.accountName
                        else accounts.data.firstOrNull { account -> account.id == transaction.accountId }?.name
                    when (result) {
                        is Resource.Loading -> it.copy(isLoading = true, accountName = accountName)
                        is Resource.Success -> it.copy(isLoading = false, transaction = result.data, error = null, accountName = accountName)
                        is Resource.Error -> it.copy(
                            isLoading = false, transaction = transaction,
                            error = if (result.cause.isSignInRequired()) TransactionMessages.SIGN_IN
                            else TransactionMessages.LOAD_ERROR, accountName = accountName
                        )
                    }
                }
            }
        }
    }

    fun showDeleteDialog() {
        if (id == null || _uiState.value.transaction == null || _uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleteDialogVisible = true) }
    }
    fun retry() {
        val transactionId = id ?: return
        viewModelScope.launch {
            val result = retryConversion(transactionId)
            _uiState.update { it.copy(error = if (result.isFailure) TransactionMessages.CURRENCY_RETRY_FAILED else null) }
        }
    }
    fun hideDeleteDialog() { _uiState.update { it.copy(isDeleteDialogVisible = false) } }

    fun confirmDelete() {
        val transactionId = id ?: return
        if (_uiState.value.isDeleting || _uiState.value.transaction == null) return
        _uiState.update { it.copy(isDeleting = true, isDeleteDialogVisible = false, error = null) }
        viewModelScope.launch {
            deleteTransaction(transactionId).onSuccess {
                _uiState.update { it.copy(isDeleting = false, isDeleted = true) }
            }.onFailure { error ->
                _uiState.update { it.copy(
                    isDeleting = false,
                    error = when {
                        error.isSignInRequired() -> TransactionMessages.SIGN_IN
                        error is TransactionException && error.error == TransactionError.INSUFFICIENT_BALANCE ->
                            TransactionMessages.DELETE_BLOCKED_BALANCE
                        else -> TransactionMessages.DELETE_ERROR
                    }
                ) }
            }
        }
    }
}
