package com.example.clarity.presentation.accounts.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.R
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.usecase.account.SetDefaultAccountUseCase
import com.example.clarity.domain.util.Resource
import com.example.clarity.presentation.accounts.accountErrorResource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountListViewModel @Inject constructor(
    private val getAccounts: GetAccountsUseCase,
    private val setDefaultAccount: SetDefaultAccountUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountListState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(getAccounts(), authRepository.currentUser) { result, user -> result to user }.collect { (result, user) ->
                _uiState.update { state ->
                    when (result) {
                        is Resource.Loading -> state.copy(isLoading = true)
                        is Resource.Success -> state.copy(isLoading = false, accounts = result.data.orEmpty(), error = null)
                        is Resource.Error -> state.copy(
                            isLoading = false, accounts = result.data.orEmpty(),
                            error = if (user == null) R.string.accounts_sign_in else R.string.accounts_load_error
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
                    accountErrorResource(error, R.string.accounts_default_error)
                }
            ) }
        }
    }
}
