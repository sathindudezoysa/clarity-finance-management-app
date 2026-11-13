package com.example.clarity.presentation.wallet

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.Currencies
import com.example.clarity.domain.model.CurrencyConversion
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionTags
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.repository.ExchangeRateRepository
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.usecase.currency.ObserveCurrencyPreferenceUseCase
import com.example.clarity.domain.usecase.transaction.AddTransactionUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionsUseCase
import com.example.clarity.domain.util.CashTopUpPolicy
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
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CashWalletViewModel @Inject constructor(
    private val prefs: SharedPreferences,
    private val rates: ExchangeRateRepository,
    getAccounts: GetAccountsUseCase,
    getTransactions: GetTransactionsUseCase,
    private val addTransaction: AddTransactionUseCase,
    observeCurrency: ObserveCurrencyPreferenceUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(loadQuickState())
    val uiState: StateFlow<CashWalletState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                getAccounts(),
                getTransactions().catch { emit(Resource.Success(emptyList())) },
                observeCurrency()
            ) { accountsResult, txResult, pref ->
                val accounts = accountsResult.data ?: return@combine null
                val transactions = txResult.data ?: emptyList()
                val targetCurrency = pref.currency

                val cashAccount = accounts.firstOrNull { it.isCashWallet }
                val sourceAccounts = CashTopUpPolicy.eligibleSourceAccounts(accounts, targetCurrency)
                val hasOtherCurrencyAccounts = sourceAccounts.isEmpty() &&
                    accounts.any { !it.isCashWallet && !it.isGoalAccount }

                val rateCache = mutableMapOf<String, BigDecimal?>()

                suspend fun resolveRate(source: String, target: String): BigDecimal? {
                    if (source == target) return BigDecimal.ONE
                    return rateCache.getOrPut("$source->$target") {
                        val quote = runCatching {
                            rates.cached(source, target) ?: rates.latest(source, target)
                        }.getOrNull()
                        quote?.let { runCatching { CurrencyConversion.rate(it.rate) }.getOrNull() }
                    }
                }

                suspend fun convertMinor(amountMinor: BigInteger, sourceCurrency: String, target: String): BigInteger {
                    if (amountMinor == BigInteger.ZERO || sourceCurrency == target) return amountMinor
                    val rate = resolveRate(sourceCurrency, target) ?: return BigInteger.ZERO
                    return BigDecimal(amountMinor, Currencies.digits(sourceCurrency))
                        .multiply(rate)
                        .movePointRight(Currencies.digits(target))
                        .setScale(0, RoundingMode.HALF_UP)
                        .toBigInteger()
                }

                suspend fun resolveTransactionMinor(tx: Transaction): BigInteger {
                    if (tx.currency == targetCurrency) return BigInteger.valueOf(tx.amountMinor)
                    val snapshot = tx.conversion
                    if (tx.targetCurrency == targetCurrency && snapshot != null) {
                        return BigInteger.valueOf(snapshot.amountMinor)
                    }
                    return convertMinor(BigInteger.valueOf(tx.amountMinor), tx.currency, targetCurrency)
                }

                val cashTx = transactions.filter { tx ->
                    (cashAccount != null && tx.accountId == cashAccount.id) || tx.isCash
                }

                var balanceMinor = BigInteger.ZERO

                for (tx in cashTx) {
                    val amount = resolveTransactionMinor(tx)
                    if (tx.type == TransactionType.INCOME) {
                        balanceMinor += amount
                    } else {
                        balanceMinor -= amount
                    }
                }

                val current = _uiState.value
                val selectedSourceId = CashTopUpPolicy.resolveSelectedSourceAccountId(
                    currentSelectedId = current.selectedSourceAccountId,
                    eligibleAccounts = sourceAccounts
                )

                current.copy(
                    currency = targetCurrency,
                    balance = MoneyUtils.formatMinor(balanceMinor, targetCurrency),
                    cashAccount = cashAccount,
                    sourceAccounts = sourceAccounts,
                    hasOtherCurrencyAccounts = hasOtherCurrencyAccounts,
                    selectedSourceAccountId = selectedSourceId
                )
            }.collect { updated ->
                if (updated != null) {
                    _uiState.value = updated
                    saveQuickState(updated)
                }
            }
        }
    }

    fun selectSourceAccount(accountId: String) {
        val current = _uiState.value
        if (current.isSaving) return
        if (current.sourceAccounts.none { it.id == accountId }) return
        _uiState.update { it.copy(selectedSourceAccountId = accountId) }
    }

    fun setAmount(amount: String) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(amount = amount) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSaving) return
        val cashAccount = state.cashAccount ?: return
        val defaultCurrency = state.currency
        val amountMinor = MoneyUtils.parseMinor(state.amount, defaultCurrency).getOrNull() ?: return
        if (amountMinor <= 0L) return

        val sourceAccount = state.sourceAccounts
            .firstOrNull { it.id == state.selectedSourceAccountId }
            ?.takeIf { CashTopUpPolicy.canTopUpFrom(it, defaultCurrency) }
            ?: return

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val debitTx = Transaction(
                    id = UUID.randomUUID().toString(),
                    amountMinor = amountMinor,
                    type = TransactionType.EXPENSE,
                    category = TransactionTags.CASH_TOP_UP,
                    dateEpochMillis = now,
                    note = "Top up to ${cashAccount.name}",
                    accountId = sourceAccount.id,
                    currency = defaultCurrency
                )
                val creditTx = Transaction(
                    id = UUID.randomUUID().toString(),
                    amountMinor = amountMinor,
                    type = TransactionType.INCOME,
                    category = TransactionTags.CASH_TOP_UP,
                    dateEpochMillis = now,
                    note = "Top up from ${sourceAccount.displayLabel}",
                    accountId = cashAccount.id,
                    currency = defaultCurrency
                )
                val debitRes = addTransaction(debitTx)
                if (debitRes.isSuccess) {
                    val creditRes = addTransaction(creditTx)
                    if (creditRes.isSuccess) {
                        _uiState.update { it.copy(amount = "", isSaving = false, isSaved = true) }
                        return@launch
                    }
                }
                _uiState.update { it.copy(isSaving = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun loadQuickState(): CashWalletState {
        val savedCurrency = prefs.getString(KEY_WALLET_CURRENCY, "LKR")
            ?.takeIf { runCatching { Currencies.digits(it) }.isSuccess } ?: "LKR"
        val zero = MoneyUtils.formatMinor(BigInteger.ZERO, savedCurrency)
        return CashWalletState(
            currency = savedCurrency,
            balance = prefs.getString(KEY_WALLET_BALANCE, zero) ?: zero
        )
    }

    private fun saveQuickState(state: CashWalletState) {
        prefs.edit()
            .putString(KEY_WALLET_CURRENCY, state.currency)
            .putString(KEY_WALLET_BALANCE, state.balance)
            .apply()
    }

    private companion object {
        const val KEY_WALLET_CURRENCY = "cash_wallet_currency"
        const val KEY_WALLET_BALANCE = "cash_wallet_balance"
    }
}
