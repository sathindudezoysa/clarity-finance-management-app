package com.example.clarity.presentation.transactions.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.R
import com.example.clarity.domain.usecase.transaction.GetTransactionsUseCase
import com.example.clarity.domain.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionListViewModel @Inject constructor(
    private val getTransactions: GetTransactionsUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(TransactionListState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getTransactions().collect { result ->
                _uiState.update {
                    when (result) {
                        is Resource.Loading -> TransactionListState()
                        is Resource.Success -> TransactionListState(false, result.data.orEmpty())
                        is Resource.Error -> TransactionListState(
                            false, result.data.orEmpty(),
                            if (result.message == "Please sign in") R.string.transactions_sign_in
                            else R.string.transactions_load_error
                        )
                    }
                }
            }
        }
    }
}
