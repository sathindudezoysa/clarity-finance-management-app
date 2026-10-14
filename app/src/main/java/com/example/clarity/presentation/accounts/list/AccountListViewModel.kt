package com.example.clarity.presentation.accounts.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.R
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.usecase.account.SetDefaultAccountUseCase
import com.example.clarity.domain.util.Resource
import com.example.clarity.presentation.accounts.accountErrorResource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountListViewModel @Inject constructor(
    private val getAccounts: GetAccountsUseCase,
    private val setDefaultAccount: SetDefaultAccountUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountListState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getAccounts().collect { result ->
                _uiState.update { state ->
                    when (result) {
                        is Resource.Loading -> state.copy(isLoading = true)
                        is Resource.Success -> state.copy(isLoading = false, accounts = result.data.orEmpty(), error = null)
                        is Resource.Error -> state.copy(
                            isLoading = false, accounts = result.data.orEmpty(),
                            error = accountErrorResource(result.message, R.string.accounts_load_error)
                        )
                    }
                }
            }
        }
    }

    fun makeDefault(id: String) {
        val state = _uiState.value
        if (state.changingDefaultId != null || state.accounts.none { it.id == id && !it.isDefault }) return
        _uiState.update { it.copy(changingDefaultId = id, actionError = null) }
        viewModelScope.launch {
            val result = setDefaultAccount(id)
            _uiState.update { it.copy(
                changingDefaultId = null,
                actionError = result.exceptionOrNull()?.let { error ->
                    accountErrorResource(error.message, R.string.accounts_default_error)
                }
            ) }
        }
    }
}
