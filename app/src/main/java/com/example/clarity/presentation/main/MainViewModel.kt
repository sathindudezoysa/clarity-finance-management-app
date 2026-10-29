package com.example.clarity.presentation.main

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.Currencies
import com.example.clarity.domain.model.CurrencyConversion
import com.example.clarity.domain.model.IncomeSources
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.repository.ExchangeRateRepository
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.usecase.currency.ObserveCurrencyPreferenceUseCase
import com.example.clarity.domain.usecase.income.ObserveIncomeSourcesUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionsUseCase
import com.example.clarity.domain.usecase.transaction.ObserveTransactionProofsUseCase
import com.example.clarity.domain.util.MoneyUtils
import com.example.clarity.domain.util.Resource
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
class MainViewModel @Inject constructor(
    private val prefs: SharedPreferences,
    private val rates: ExchangeRateRepository,
    getAccounts: GetAccountsUseCase,
    getTransactions: GetTransactionsUseCase,
    observeIncomeSources: ObserveIncomeSourcesUseCase,
    observeCurrency: ObserveCurrencyPreferenceUseCase,
    observeProofs: ObserveTransactionProofsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(loadQuickState())
    val uiState: StateFlow<MainState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeProofs.observeAttachedTransactionIds().collect { receiptIds ->
                _uiState.update { it.copy(receiptTransactionIds = receiptIds) }
            }
        }
        viewModelScope.launch {
            combine(
                getAccounts().catch { emit(Resource.Success(emptyList())) },
                getTransactions().catch { emit(Resource.Success(emptyList())) },
                observeIncomeSources().catch { emit(IncomeSources(null, emptyList())) },
                observeCurrency()
            ) { accountsResult, txResult, incomeSources, pref ->
                val targetCurrency = pref.currency
                val accounts = accountsResult.data ?: return@combine null
                val transactions = txResult.data ?: return@combine null
                val accountNames = accounts.associate { it.id to it.name }
                val recentTransactions = transactions
                    .sortedWith(compareByDescending<Transaction> { it.dateEpochMillis }.thenByDescending { it.createdAt })
                    .take(5)
                val rateCache = mutableMapOf<String, BigDecimal?>()

                suspend fun resolveRate(source: String): BigDecimal? {
                    if (source == targetCurrency) return BigDecimal.ONE
                    return rateCache.getOrPut(source) {
                        val quote = runCatching {
                            rates.cached(source, targetCurrency) ?: rates.latest(source, targetCurrency)
                        }.getOrNull()
                        quote?.let { runCatching { CurrencyConversion.rate(it.rate) }.getOrNull() }
                    }
                }

                suspend fun convertMinor(amountMinor: BigInteger, sourceCurrency: String): BigInteger {
                    if (amountMinor == BigInteger.ZERO || sourceCurrency == targetCurrency) return amountMinor
                    val rate = resolveRate(sourceCurrency) ?: return BigInteger.ZERO
                    return BigDecimal(amountMinor, Currencies.digits(sourceCurrency))
                        .multiply(rate)
                        .movePointRight(Currencies.digits(targetCurrency))
                        .setScale(0, RoundingMode.HALF_UP)
                        .toBigInteger()
                }

                suspend fun resolveTransactionMinor(tx: Transaction): BigInteger {
                    if (tx.currency == targetCurrency) return BigInteger.valueOf(tx.amountMinor)
                    val snapshot = tx.conversion
                    if (tx.targetCurrency == targetCurrency && snapshot != null) {
                        return BigInteger.valueOf(snapshot.amountMinor)
                    }
                    return convertMinor(BigInteger.valueOf(tx.amountMinor), tx.currency)
                }

                val cashAccount = accounts.firstOrNull { it.isCashWallet }
                var cashBalanceMinor = BigInteger.ZERO

                var incomeMinor = BigInteger.ZERO
                var expenseMinor = BigInteger.ZERO
                var netWorthMinor = BigInteger.ZERO
                var expenseCount = 0

                for (tx in transactions) {
                    val amount = resolveTransactionMinor(tx)
                    val isCashTx = (cashAccount != null && tx.accountId == cashAccount.id) || tx.isCash
                    val isTopUp = tx.category == "Cash Top-Up"

                    if (tx.type == TransactionType.INCOME) {
                        netWorthMinor += amount
                        if (isCashTx) cashBalanceMinor += amount
                        if (!isTopUp) incomeMinor += amount
                    } else {
                        netWorthMinor -= amount
                        if (isCashTx) cashBalanceMinor -= amount
                        if (!isTopUp) {
                            expenseMinor += amount
                            expenseCount++
                        }
                    }
                }

                val activeSources = incomeSources.sources.filter { it.active }.map { it.name }
                val incomeSubtitle = when {
                    activeSources.isEmpty() -> "No income sources"
                    activeSources.size <= 2 -> activeSources.joinToString(" & ")
                    else -> "${activeSources.take(2).joinToString(", ")} +${activeSources.size - 2}"
                }

                val expenseSubtitle = if (incomeMinor > BigInteger.ZERO) {
                    val pct = BigDecimal(expenseMinor)
                        .multiply(BigDecimal(100))
                        .divide(BigDecimal(incomeMinor), 1, RoundingMode.HALF_UP)
                    "$pct% of income pool"
                } else {
                    "$expenseCount transactions"
                }

                MainState(
                    isHidden = _uiState.value.isHidden,
                    currency = targetCurrency,
                    netWorth = MoneyUtils.formatMinor(netWorthMinor, targetCurrency),
                    income = "+${MoneyUtils.formatMinor(incomeMinor, targetCurrency)}",
                    incomeSubtitle = incomeSubtitle,
                    expenses = "-${MoneyUtils.formatMinor(expenseMinor, targetCurrency)}",
                    expenseSubtitle = expenseSubtitle,
                    cashBalance = MoneyUtils.formatMinor(cashBalanceMinor, targetCurrency),
                    recentTransactions = recentTransactions,
                    accountNames = accountNames,
                    receiptTransactionIds = _uiState.value.receiptTransactionIds
                )
            }.collect { updated ->
                if (updated != null) {
                    _uiState.value = updated
                    saveQuickState(updated)
                }
            }
        }
    }

    fun togglePrivacy() {
        val next = !_uiState.value.isHidden
        _uiState.update { it.copy(isHidden = next) }
        prefs.edit().putBoolean(KEY_IS_HIDDEN, next).apply()
    }

    private fun loadQuickState(): MainState {
        val savedCurrency = prefs.getString(KEY_CURRENCY, "LKR")
            ?.takeIf { runCatching { Currencies.digits(it) }.isSuccess } ?: "LKR"
        val zero = MoneyUtils.formatMinor(BigInteger.ZERO, savedCurrency)
        return MainState(
            isHidden = prefs.getBoolean(KEY_IS_HIDDEN, false),
            currency = savedCurrency,
            netWorth = prefs.getString(KEY_NET_WORTH, zero) ?: zero,
            income = prefs.getString(KEY_INCOME, "+$zero") ?: "+$zero",
            incomeSubtitle = prefs.getString(KEY_INCOME_SUB, "No income sources") ?: "No income sources",
            expenses = prefs.getString(KEY_EXPENSES, "-$zero") ?: "-$zero",
            expenseSubtitle = prefs.getString(KEY_EXPENSE_SUB, "0 transactions") ?: "0 transactions",
            cashBalance = prefs.getString(KEY_CASH_BALANCE, zero) ?: zero
        )
    }

    private fun saveQuickState(state: MainState) {
        prefs.edit()
            .putString(KEY_CURRENCY, state.currency)
            .putString(KEY_NET_WORTH, state.netWorth)
            .putString(KEY_INCOME, state.income)
            .putString(KEY_INCOME_SUB, state.incomeSubtitle)
            .putString(KEY_EXPENSES, state.expenses)
            .putString(KEY_EXPENSE_SUB, state.expenseSubtitle)
            .putString(KEY_CASH_BALANCE, state.cashBalance)
            .apply()
    }

    private companion object {
        const val KEY_IS_HIDDEN = "dashboard_is_hidden"
        const val KEY_CURRENCY = "dashboard_currency"
        const val KEY_NET_WORTH = "dashboard_net_worth"
        const val KEY_INCOME = "dashboard_income"
        const val KEY_INCOME_SUB = "dashboard_income_subtitle"
        const val KEY_EXPENSES = "dashboard_expenses"
        const val KEY_EXPENSE_SUB = "dashboard_expense_subtitle"
        const val KEY_CASH_BALANCE = "dashboard_cash_balance"
    }
}
