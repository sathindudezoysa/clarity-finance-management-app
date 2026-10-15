package com.example.clarity.presentation.transactions.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.R
import com.example.clarity.domain.usecase.transaction.GetTransactionsUseCase
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionListViewModel @Inject constructor(
    private val getTransactions: GetTransactionsUseCase,
    private val getAccounts: GetAccountsUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(TransactionListState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(getTransactions(), getAccounts()) { transactions, accounts -> transactions to accounts }.collect { (result, accounts) ->
                _uiState.update {
                    val accountNames = accounts.data?.associate { account -> account.id to account.name } ?: it.accountNames
                    when (result) {
                        is Resource.Loading -> it.copy(isLoading = true, accountNames = accountNames)
                        is Resource.Success -> it.copy(isLoading = false, transactions = result.data.orEmpty(), error = null,
                            accountNames = accountNames)
                        is Resource.Error -> it.copy(
                            isLoading = false, transactions = result.data.orEmpty(),
                            error = if (result.message == "Please sign in") R.string.transactions_sign_in
                            else R.string.transactions_load_error, accountNames = accountNames
                        )
                    }
                }
            }
        }
    }
}
