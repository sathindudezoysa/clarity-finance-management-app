package com.example.clarity.presentation.transactions.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.Currencies
import com.example.clarity.domain.model.CurrencyConversion
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.repository.ExchangeRateRepository
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.usecase.currency.ObserveCurrencyPreferenceUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionsUseCase
import com.example.clarity.domain.usecase.transaction.ObserveTransactionProofsUseCase
import com.example.clarity.domain.util.Resource
import com.example.clarity.domain.util.isSignInRequired
import com.example.clarity.presentation.transactions.TransactionMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject

@HiltViewModel
class TransactionListViewModel @Inject constructor(
    private val getTransactions: GetTransactionsUseCase,
    private val getAccounts: GetAccountsUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(TransactionListState())
    val uiState = _uiState.asStateFlow()

    private val _preferredCurrency = MutableStateFlow<String?>(null)
    val preferredCurrency = _preferredCurrency.asStateFlow()

    private val _receiptTransactionIds = MutableStateFlow<Set<String>>(emptySet())
    val receiptTransactionIds = _receiptTransactionIds.asStateFlow()

    private val _accountsList = MutableStateFlow<List<com.example.clarity.domain.model.Account>>(emptyList())
    val accountsList = _accountsList.asStateFlow()

    private val _convertedAmountsMinor = MutableStateFlow<Map<String, BigInteger>>(emptyMap())
    val convertedAmountsMinor = _convertedAmountsMinor.asStateFlow()

    private var exchangeRates: ExchangeRateRepository? = null

    @Inject
    internal fun injectExtras(
        observeCurrency: ObserveCurrencyPreferenceUseCase,
        observeProofs: ObserveTransactionProofsUseCase,
        rates: ExchangeRateRepository
    ) {
        this.exchangeRates = rates
        viewModelScope.launch {
            observeCurrency().collect { pref ->
                _preferredCurrency.value = pref.currency
                recomputeConversions(_uiState.value.transactions, pref.currency)
            }
        }
        viewModelScope.launch {
            observeProofs.observeAttachedTransactionIds().collect { ids ->
                _receiptTransactionIds.value = ids
            }
        }
    }

    init {
        viewModelScope.launch {
            combine(getTransactions(), getAccounts()) { transactions, accounts -> transactions to accounts }.collect { (result, accounts) ->
                accounts.data?.let { _accountsList.value = it }
                _uiState.update {
                    val accountNames = accounts.data?.associate { account -> account.id to account.name } ?: it.accountNames
                    when (result) {
                        is Resource.Loading -> it.copy(isLoading = true, accountNames = accountNames)
                        is Resource.Success -> it.copy(isLoading = false, transactions = result.data.orEmpty(), error = null,
                            accountNames = accountNames)
                        is Resource.Error -> it.copy(
                            isLoading = false, transactions = if (result.cause.isSignInRequired()) result.data.orEmpty()
                                else result.data ?: it.transactions,
                            error = if (result.cause.isSignInRequired()) TransactionMessages.SIGN_IN
                            else TransactionMessages.LOAD_ERROR, accountNames = accountNames
                        )
                    }
                }
                val activeCurrency = _preferredCurrency.value
                    ?: _uiState.value.transactions.firstOrNull()?.targetCurrency
                    ?: _accountsList.value.firstOrNull { it.isDefault }?.currency
                    ?: _accountsList.value.firstOrNull()?.currency
                if (activeCurrency != null) {
                    recomputeConversions(_uiState.value.transactions, activeCurrency)
                }
            }
        }
    }

    private fun recomputeConversions(transactions: List<Transaction>, targetCurrency: String) {
        val ratesRepo = exchangeRates ?: return
        viewModelScope.launch {
            val rateCache = mutableMapOf<String, BigDecimal?>()
            val mapped = mutableMapOf<String, BigInteger>()
            for (tx in transactions) {
                val resolved = when {
                    tx.currency.equals(targetCurrency, ignoreCase = true) -> BigInteger.valueOf(tx.amountMinor)
                    tx.targetCurrency.equals(targetCurrency, ignoreCase = true) && tx.conversion != null ->
                        BigInteger.valueOf(tx.conversion.amountMinor)
                    else -> {
                        val rate = rateCache.getOrPut(tx.currency) {
                            val quote = runCatching {
                                ratesRepo.cached(tx.currency, targetCurrency) ?: ratesRepo.latest(tx.currency, targetCurrency)
                            }.getOrNull()
                            quote?.let { runCatching { CurrencyConversion.rate(it.rate) }.getOrNull() }
                        }
                        if (rate != null) {
                            BigDecimal(BigInteger.valueOf(tx.amountMinor), Currencies.digits(tx.currency))
                                .multiply(rate)
                                .movePointRight(Currencies.digits(targetCurrency))
                                .setScale(0, RoundingMode.HALF_UP)
                                .toBigInteger()
                        } else {
                            BigInteger.valueOf(tx.amountMinor)
                        }
                    }
                }
                mapped[tx.id] = resolved
            }
            _convertedAmountsMinor.value = mapped
        }
    }
}
