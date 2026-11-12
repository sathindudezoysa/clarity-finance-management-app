package com.example.clarity.presentation.accounts.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.Currencies
import com.example.clarity.domain.model.CurrencyConversion
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.repository.ExchangeRateRepository
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.usecase.account.SetDefaultAccountUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionsUseCase
import com.example.clarity.domain.usecase.transaction.ObserveTransactionProofsUseCase
import com.example.clarity.domain.util.AccountBalancePolicy
import com.example.clarity.domain.util.MoneyUtils
import com.example.clarity.domain.util.Resource
import com.example.clarity.presentation.accounts.AccountMessages
import com.example.clarity.presentation.accounts.accountErrorMessage
import com.example.clarity.presentation.transactions.formatTransactionDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAccounts: GetAccountsUseCase,
    private val getTransactions: GetTransactionsUseCase,
    private val setDefaultAccount: SetDefaultAccountUseCase,
    private val authRepository: AuthRepository,
    private val rates: ExchangeRateRepository,
    private val observeProofs: ObserveTransactionProofsUseCase? = null
) : ViewModel() {

    private val accountId: String? = (savedStateHandle.get<Any?>("id") as? String)
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it.length <= 128 && !it.contains('/') }

    private val _uiState = MutableStateFlow(
        AccountDetailState(
            accountId = accountId,
            isLoading = accountId != null,
            error = if (accountId == null) AccountMessages.NOT_FOUND else null
        )
    )
    val uiState: StateFlow<AccountDetailState> = _uiState.asStateFlow()

    private val receiptIdsFlow = MutableStateFlow<Set<String>>(emptySet())

    init {
        if (accountId != null) {
            if (observeProofs != null) {
                viewModelScope.launch {
                    observeProofs.observeAttachedTransactionIds()
                        .catch { emit(emptySet()) }
                        .collect { ids ->
                            receiptIdsFlow.value = ids
                        }
                }
            }
            observeAccountDetails(accountId)
        }
    }

    private fun observeAccountDetails(targetAccountId: String) {
        viewModelScope.launch {
            combine(
                getAccounts(),
                getTransactions().catch { emit(Resource.Success(emptyList())) },
                authRepository.currentUser,
                receiptIdsFlow
            ) { accountsResult, txResult, user, receiptIds ->
                Quadruple(accountsResult, txResult, user, receiptIds)
            }.collect { (accountsResult, txResult, user, receiptIds) ->
                if (accountsResult is Resource.Loading && _uiState.value.account == null) {
                    _uiState.update { it.copy(isLoading = true) }
                    return@collect
                }

                val accounts = accountsResult.data.orEmpty()
                val account = accounts.firstOrNull { it.id == targetAccountId }

                if (account == null) {
                    val errorMsg = when {
                        user == null -> AccountMessages.SIGN_IN
                        accountsResult is Resource.Error -> AccountMessages.LOAD_ERROR
                        else -> AccountMessages.NOT_FOUND
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            account = null,
                            error = errorMsg
                        )
                    }
                    return@collect
                }

                val accountCurrency = account.currency.ifBlank { Currencies.supported.first() }
                val allTransactions = txResult.data.orEmpty()
                val accountTransactions = allTransactions
                    .filter { tx ->
                        tx.accountId == account.id || (account.isCashWallet && tx.isCash)
                    }
                    .sortedWith(
                        compareByDescending<Transaction> { it.dateEpochMillis }
                            .thenByDescending { it.createdAt }
                    )

                val rateCache = mutableMapOf<String, BigDecimal?>()
                suspend fun resolveRate(source: String, target: String): BigDecimal? {
                    if (source.equals(target, ignoreCase = true)) return BigDecimal.ONE
                    return rateCache.getOrPut("$source->$target") {
                        val quote = runCatching {
                            rates.cached(source, target) ?: rates.latest(source, target)
                        }.getOrNull()
                        quote?.let { runCatching { CurrencyConversion.rate(it.rate) }.getOrNull() }
                    }
                }

                suspend fun resolveTxAmountMinor(tx: Transaction): BigInteger {
                    if (tx.amountMinor <= 0L) return BigInteger.ZERO
                    if (tx.currency.equals(accountCurrency, ignoreCase = true)) {
                        return BigInteger.valueOf(tx.amountMinor)
                    }
                    val snapshot = tx.conversion
                    if (tx.targetCurrency.equals(accountCurrency, ignoreCase = true) &&
                        !tx.pendingConversion &&
                        snapshot != null &&
                        snapshot.amountMinor > 0L
                    ) {
                        return BigInteger.valueOf(snapshot.amountMinor)
                    }
                    val rate = resolveRate(tx.currency, accountCurrency)
                    if (rate != null) {
                        return runCatching {
                            BigDecimal(BigInteger.valueOf(tx.amountMinor), Currencies.digits(tx.currency))
                                .multiply(rate)
                                .movePointRight(Currencies.digits(accountCurrency))
                                .setScale(0, RoundingMode.HALF_UP)
                                .toBigInteger()
                        }.getOrDefault(BigInteger.valueOf(tx.amountMinor))
                    }
                    return BigInteger.valueOf(tx.amountMinor)
                }

                var balanceMinor = BigInteger.ZERO
                var incomeMinor = BigInteger.ZERO
                var expenseMinor = BigInteger.ZERO

                val txItems = accountTransactions.map { tx ->
                    val resolvedMinor = resolveTxAmountMinor(tx)
                    if (tx.type == TransactionType.INCOME) {
                        balanceMinor += resolvedMinor
                        incomeMinor += resolvedMinor
                    } else {
                        balanceMinor -= resolvedMinor
                        expenseMinor += resolvedMinor
                    }
                    val sign = if (tx.type == TransactionType.INCOME) "+" else "-"
                    val formattedAmt = "$sign$accountCurrency ${MoneyUtils.formatMinor(resolvedMinor, accountCurrency)}"
                    AccountTransactionItemUiModel(
                        transaction = tx,
                        resolvedAmountMinor = resolvedMinor,
                        formattedAmount = formattedAmt,
                        formattedDate = formatTransactionDate(tx.dateEpochMillis),
                        hasReceipt = tx.id in receiptIds
                    )
                }

                val otherAccountsCount = accounts.count { !it.isGoalAccount && it.id != account.id }

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        account = account,
                        balanceMinor = balanceMinor,
                        balanceFormatted = AccountBalancePolicy.formatAccountBalance(account, balanceMinor),
                        totalIncomeFormatted = "+$accountCurrency ${MoneyUtils.formatMinor(incomeMinor, accountCurrency)}",
                        totalExpenseFormatted = "-$accountCurrency ${MoneyUtils.formatMinor(expenseMinor, accountCurrency)}",
                        transactions = txItems,
                        otherAccountsCount = otherAccountsCount,
                        error = if (accountsResult is Resource.Error) {
                            if (user == null) AccountMessages.SIGN_IN else AccountMessages.LOAD_ERROR
                        } else null
                    )
                }
            }
        }
    }

    fun makeDefault() {
        val currentAccount = _uiState.value.account ?: return
        if (currentAccount.isDefault || currentAccount.isCashWallet || _uiState.value.isMakingDefault) return
        _uiState.update { it.copy(isMakingDefault = true, actionError = null) }
        viewModelScope.launch {
            val result = setDefaultAccount(currentAccount.id)
            _uiState.update {
                it.copy(
                    isMakingDefault = false,
                    actionError = result.exceptionOrNull()?.let { error ->
                        accountErrorMessage(error, AccountMessages.DEFAULT_ERROR)
                    }
                )
            }
        }
    }

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
}
