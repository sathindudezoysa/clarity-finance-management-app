package com.example.clarity.presentation.accounts.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.CurrencyConversion
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.repository.ExchangeRateRepository
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.usecase.account.SetDefaultAccountUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionsUseCase
import com.example.clarity.domain.util.AccountBalancePolicy
import com.example.clarity.domain.util.Resource
import com.example.clarity.presentation.accounts.AccountMessages
import com.example.clarity.presentation.accounts.accountErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class AccountListViewModel @Inject constructor(
    private val getAccounts: GetAccountsUseCase,
    private val setDefaultAccount: SetDefaultAccountUseCase,
    private val authRepository: AuthRepository,
    private val getTransactions: GetTransactionsUseCase? = null,
    private val rates: ExchangeRateRepository? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountListState())
    val uiState = _uiState.asStateFlow()

    init {
        val txFlow = getTransactions?.invoke()?.catch { emit(Resource.Success(emptyList())) }
        viewModelScope.launch {
            if (txFlow != null) {
                combine(getAccounts(), txFlow, authRepository.currentUser) { accountsResult, txResult, user ->
                    Triple(accountsResult, txResult, user)
                }.collect { (accountsResult, txResult, user) ->
                    val accounts = accountsResult.data.orEmpty()
                    val transactions = txResult.data.orEmpty()
                    val rateCache = mutableMapOf<String, BigDecimal?>()

                    suspend fun resolveRate(source: String, target: String): BigDecimal? {
                        if (source == target) return BigDecimal.ONE
                        if (rates == null) return null
                        return rateCache.getOrPut("$source->$target") {
                            val quote = runCatching {
                                rates.cached(source, target) ?: rates.latest(source, target)
                            }.getOrNull()
                            quote?.let { runCatching { CurrencyConversion.rate(it.rate) }.getOrNull() }
                        }
                    }

                    val balances = accounts.associate { account ->
                        val balanceMinor = AccountBalancePolicy.calculateBalanceMinor(
                            account = account,
                            transactions = transactions,
                            resolveRate = ::resolveRate
                        )
                        account.id to AccountBalancePolicy.formatAccountBalance(account, balanceMinor)
                    }

                    _uiState.update { state ->
                        when (accountsResult) {
                            is Resource.Loading -> state.copy(isLoading = true)
                            is Resource.Success -> state.copy(
                                isLoading = false,
                                accounts = accounts,
                                accountBalances = balances,
                                error = null
                            )
                            is Resource.Error -> state.copy(
                                isLoading = false,
                                accounts = accounts,
                                accountBalances = balances,
                                error = if (user == null) AccountMessages.SIGN_IN else AccountMessages.LOAD_ERROR
                            )
                        }
                    }
                }
            } else {
                combine(getAccounts(), authRepository.currentUser) { result, user -> result to user }.collect { (result, user) ->
                    val accounts = result.data.orEmpty()
                    val balances = accounts.associate { account ->
                        account.id to AccountBalancePolicy.formatAccountBalance(account, java.math.BigInteger.ZERO)
                    }
                    _uiState.update { state ->
                        when (result) {
                            is Resource.Loading -> state.copy(isLoading = true)
                            is Resource.Success -> state.copy(
                                isLoading = false,
                                accounts = accounts,
                                accountBalances = balances,
                                error = null
                            )
                            is Resource.Error -> state.copy(
                                isLoading = false,
                                accounts = accounts,
                                accountBalances = balances,
                                error = if (user == null) AccountMessages.SIGN_IN else AccountMessages.LOAD_ERROR
                            )
                        }
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
                    accountErrorMessage(error, AccountMessages.DEFAULT_ERROR)
                }
            ) }
        }
    }
}
