package com.example.clarity.presentation.accounts.transfer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.AccountError
import com.example.clarity.domain.model.AccountException
import com.example.clarity.domain.model.CurrencyConversion
import com.example.clarity.domain.model.TransactionError
import com.example.clarity.domain.model.TransactionException
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.repository.ExchangeRateRepository
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.usecase.account.TransferMoneyUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionsUseCase
import com.example.clarity.domain.usecase.transaction.PreviewConversionUseCase
import com.example.clarity.domain.util.AccountBalancePolicy
import com.example.clarity.domain.util.MoneyUtils
import com.example.clarity.domain.util.Resource
import com.example.clarity.presentation.accounts.AccountMessages
import com.example.clarity.presentation.transactions.TransactionMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

@HiltViewModel
class AccountTransferViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAccounts: GetAccountsUseCase,
    private val getTransactions: GetTransactionsUseCase,
    private val transferMoney: TransferMoneyUseCase,
    private val previewConversion: PreviewConversionUseCase,
    private val rates: ExchangeRateRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val initialSourceId: String? = (savedStateHandle.get<Any?>("sourceId") as? String)
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it.length <= 128 && !it.contains('/') }

    private val _uiState = MutableStateFlow(
        AccountTransferState(
            isLoading = true,
            sourceAccountId = initialSourceId
        )
    )
    val uiState: StateFlow<AccountTransferState> = _uiState.asStateFlow()

    private var previewJob: Job? = null

    init {
        observeAccountsAndBalances()
    }

    private fun observeAccountsAndBalances() {
        viewModelScope.launch {
            combine(
                getAccounts(),
                getTransactions().catch { emit(Resource.Success(emptyList())) },
                authRepository.currentUser
            ) { accountsResult, txResult, user ->
                Triple(accountsResult, txResult, user)
            }.collect { (accountsResult, txResult, user) ->
                if (accountsResult is Resource.Loading && _uiState.value.accounts.isEmpty()) {
                    _uiState.update { it.copy(isLoading = true) }
                    return@collect
                }

                val eligibleAccounts = accountsResult.data.orEmpty()
                    .filterNot { it.isGoalAccount }

                val transactions = txResult.data.orEmpty()
                val rateCache = mutableMapOf<String, BigDecimal?>()
                suspend fun resolveRate(source: String, target: String): BigDecimal? {
                    if (source.equals(target, ignoreCase = true)) return BigDecimal.ONE
                    val key = "$source->$target"
                    if (rateCache.containsKey(key)) {
                        return rateCache[key]
                    }
                    val quote = try {
                        rates.cached(source, target) ?: rates.latest(source, target)
                    } catch (_: Exception) {
                        null
                    }
                    val resolved = quote?.let {
                        try {
                            CurrencyConversion.rate(it.rate)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    rateCache[key] = resolved
                    return resolved
                }

                val balancesMinor = mutableMapOf<String, BigInteger>()
                val balancesFormatted = mutableMapOf<String, String>()
                for (account in eligibleAccounts) {
                    val balanceMinor = AccountBalancePolicy.calculateBalanceMinor(
                        account = account,
                        transactions = transactions,
                        resolveRate = ::resolveRate
                    )
                    balancesMinor[account.id] = balanceMinor
                    balancesFormatted[account.id] = AccountBalancePolicy.formatAccountBalance(account, balanceMinor)
                }

                val currentState = _uiState.value
                val resolvedSourceId = currentState.sourceAccountId
                    ?.takeIf { id -> eligibleAccounts.any { it.id == id } }
                    ?: initialSourceId?.takeIf { id -> eligibleAccounts.any { it.id == id } }
                    ?: eligibleAccounts.firstOrNull { it.isDefault }?.id
                    ?: eligibleAccounts.firstOrNull()?.id

                val resolvedDestinationId = currentState.destinationAccountId
                    ?.takeIf { id -> id != resolvedSourceId && eligibleAccounts.any { it.id == id } }
                    ?: eligibleAccounts.firstOrNull { it.id != resolvedSourceId }?.id

                val errorMsg = when {
                    user == null -> AccountMessages.SIGN_IN
                    accountsResult is Resource.Error -> AccountMessages.LOAD_ERROR
                    else -> null
                }

                val previousSourceCurrency = currentState.sourceCurrency
                val previousDestinationCurrency = currentState.destinationCurrency

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        accounts = eligibleAccounts,
                        accountBalancesMinor = balancesMinor,
                        accountBalancesFormatted = balancesFormatted,
                        sourceAccountId = resolvedSourceId,
                        destinationAccountId = resolvedDestinationId,
                        error = errorMsg
                    )
                }

                val updated = _uiState.value
                if (updated.sourceCurrency != previousSourceCurrency ||
                    updated.destinationCurrency != previousDestinationCurrency
                ) {
                    schedulePreview(updated.amountInput, immediate = true)
                }
            }
        }
    }

    fun onSourceAccountSelected(accountId: String) {
        val current = _uiState.value
        if (current.isSaving || current.isSuccess) return
        if (current.accounts.none { it.id == accountId }) return

        val nextDestinationId = if (current.destinationAccountId == accountId) {
            current.sourceAccountId?.takeIf { it != accountId }
                ?: current.accounts.firstOrNull { it.id != accountId }?.id
        } else {
            current.destinationAccountId
        }

        _uiState.update {
            it.copy(
                sourceAccountId = accountId,
                destinationAccountId = nextDestinationId,
                accountError = null,
                amountError = null,
                error = null
            )
        }
        schedulePreview(_uiState.value.amountInput, immediate = true)
    }

    fun onDestinationAccountSelected(accountId: String) {
        val current = _uiState.value
        if (current.isSaving || current.isSuccess) return
        if (current.accounts.none { it.id == accountId }) return

        val nextSourceId = if (current.sourceAccountId == accountId) {
            current.destinationAccountId?.takeIf { it != accountId }
                ?: current.accounts.firstOrNull { it.id != accountId }?.id
        } else {
            current.sourceAccountId
        }

        _uiState.update {
            it.copy(
                sourceAccountId = nextSourceId,
                destinationAccountId = accountId,
                accountError = null,
                error = null
            )
        }
        schedulePreview(_uiState.value.amountInput, immediate = true)
    }

    fun swapAccounts() {
        val current = _uiState.value
        if (current.isSaving || current.isSuccess) return
        val src = current.sourceAccountId ?: return
        val dst = current.destinationAccountId ?: return
        if (src == dst) return

        _uiState.update {
            it.copy(
                sourceAccountId = dst,
                destinationAccountId = src,
                accountError = null,
                amountError = null,
                error = null
            )
        }
        schedulePreview(_uiState.value.amountInput, immediate = true)
    }

    fun onAmountChanged(value: String) {
        if (_uiState.value.isSaving || _uiState.value.isSuccess) return
        _uiState.update {
            it.copy(
                amountInput = value,
                amountError = null,
                error = null
            )
        }
        schedulePreview(value, immediate = false)
    }

    fun onNoteChanged(value: String) {
        if (_uiState.value.isSaving || _uiState.value.isSuccess) return
        _uiState.update {
            it.copy(
                noteInput = value,
                noteError = null,
                error = null
            )
        }
    }

    fun retryPreview() {
        schedulePreview(_uiState.value.amountInput, immediate = true)
    }

    private fun schedulePreview(amountText: String, immediate: Boolean) {
        previewJob?.cancel()
        val state = _uiState.value
        if (!state.isCrossCurrency) {
            _uiState.update {
                it.copy(
                    preview = null,
                    previewLoading = false,
                    previewError = false,
                    error = if (it.previewError) null else it.error
                )
            }
            return
        }

        val amountMinor = MoneyUtils.parseMinor(amountText, state.sourceCurrency).getOrNull()
        if (amountMinor == null || amountMinor <= 0L) {
            _uiState.update {
                it.copy(
                    preview = null,
                    previewLoading = false,
                    previewError = false,
                    error = if (it.previewError) null else it.error
                )
            }
            return
        }

        val sourceCurrency = state.sourceCurrency
        val destinationCurrency = state.destinationCurrency

        previewJob = viewModelScope.launch {
            if (!immediate) {
                delay(400)
            }
            _uiState.update {
                it.copy(preview = null, previewLoading = true, previewError = false)
            }
            try {
                val snapshot = previewConversion(amountMinor, sourceCurrency, destinationCurrency)
                _uiState.update {
                    it.copy(preview = snapshot, previewLoading = false, previewError = false, error = null)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val cachedSnapshot = runCatching {
                    rates.cached(sourceCurrency, destinationCurrency)?.let { quote ->
                        CurrencyConversion.snapshot(amountMinor, quote)
                    }
                }.getOrNull()
                if (cachedSnapshot != null) {
                    _uiState.update {
                        it.copy(preview = cachedSnapshot, previewLoading = false, previewError = false, error = null)
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            preview = null,
                            previewLoading = false,
                            previewError = true,
                            error = "No network service available to convert $sourceCurrency to $destinationCurrency. Currency conversion is required to transfer between different currencies."
                        )
                    }
                }
            }
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving || state.isSuccess) return

        val sourceAccount = state.sourceAccount
        val destinationAccount = state.destinationAccount
        if (sourceAccount == null || destinationAccount == null) {
            _uiState.update {
                it.copy(accountError = "Select both source and destination accounts.")
            }
            return
        }
        if (sourceAccount.id == destinationAccount.id) {
            _uiState.update {
                it.copy(accountError = "Source and destination accounts must be different.")
            }
            return
        }

        val amountMinor = MoneyUtils.parseMinor(state.amountInput, state.sourceCurrency).getOrNull()
        val amountError = when {
            amountMinor == null -> TransactionMessages.AMOUNT_INVALID
            amountMinor <= 0L -> TransactionMessages.AMOUNT_POSITIVE
            BigInteger.valueOf(amountMinor) > state.availableSourceBalanceMinor ->
                "Amount exceeds available balance (${state.availableSourceBalanceFormatted})."
            else -> null
        }

        val noteError = if (state.noteInput.trim().length > 1800) {
            TransactionMessages.DESCRIPTION_LONG
        } else null

        if (amountError != null || noteError != null) {
            _uiState.update {
                it.copy(amountError = amountError, noteError = noteError, error = null)
            }
            return
        }

        if (state.isCrossCurrency && state.previewLoading) {
            _uiState.update {
                it.copy(error = "Waiting for exchange rate conversion...")
            }
            return
        }

        _uiState.update {
            it.copy(
                isSaving = true,
                amountError = null,
                accountError = null,
                noteError = null,
                error = null
            )
        }

        viewModelScope.launch {
            val result = transferMoney(
                sourceAccountId = sourceAccount.id,
                destinationAccountId = destinationAccount.id,
                amountText = state.amountInput,
                note = state.noteInput
            )
            result.onSuccess {
                _uiState.update {
                    it.copy(isSaving = false, isSuccess = true)
                }
            }.onFailure { error ->
                val message = when {
                    error is AccountException && error.error == AccountError.SIGN_IN_REQUIRED ->
                        TransactionMessages.SIGN_IN
                    error is TransactionException -> when (error.error) {
                        TransactionError.SIGN_IN_REQUIRED -> TransactionMessages.SIGN_IN
                        TransactionError.ACCOUNT_REQUIRED -> "Select both source and destination accounts."
                        TransactionError.ACCOUNT_NOT_FOUND -> TransactionMessages.ACCOUNT_UNAVAILABLE
                        TransactionError.INSUFFICIENT_BALANCE ->
                            error.message?.takeIf { it.isNotBlank() } ?: TransactionMessages.INSUFFICIENT_BALANCE
                    }
                    !error.message.isNullOrBlank() -> error.message
                    else -> "Unable to complete money transfer. Please try again."
                }
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        previewError = if (it.isCrossCurrency && it.preview == null) true else it.previewError,
                        error = message
                    )
                }
            }
        }
    }
}
