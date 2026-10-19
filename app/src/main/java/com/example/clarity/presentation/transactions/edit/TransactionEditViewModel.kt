package com.example.clarity.presentation.transactions.edit

import com.example.clarity.presentation.navigation.transactionIdArgument
import com.example.clarity.presentation.navigation.TRANSACTION_ID_ARGUMENT
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.R
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.AccountError
import com.example.clarity.domain.model.AccountException
import com.example.clarity.domain.model.TransactionError
import com.example.clarity.domain.model.TransactionException
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.usecase.transaction.AddTransactionUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionTagsUseCase
import com.example.clarity.domain.usecase.transaction.UpdateTransactionUseCase
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.util.MoneyUtils
import com.example.clarity.domain.util.Resource
import com.example.clarity.domain.util.isSignInRequired
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionEditViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getTransaction: GetTransactionUseCase,
    private val addTransaction: AddTransactionUseCase,
    private val updateTransaction: UpdateTransactionUseCase,
    private val getTransactionTags: GetTransactionTagsUseCase,
    private val getAccounts: GetAccountsUseCase,
    private val currencyPreference: com.example.clarity.domain.usecase.currency.ObserveCurrencyPreferenceUseCase,
    private val previewConversion: com.example.clarity.domain.usecase.transaction.PreviewConversionUseCase
) : ViewModel() {
    private val isEditMode = savedStateHandle.get<Any?>(TRANSACTION_ID_ARGUMENT) != null
    private val id = savedStateHandle.transactionIdArgument()
    private val restoredAccountId = savedStateHandle.get<Any?>("selected_account_id") as? String
    private val stateLock = Any()
    private var hasAccountData = false
    private var selectionInitialized = savedStateHandle.get<Any?>("account_selection_initialized") == true || restoredAccountId != null
    private val _uiState = MutableStateFlow(TransactionEditState(isEditMode = isEditMode, isLoading = id != null,
        selectedAccountId = restoredAccountId,
        error = if (isEditMode && id == null) R.string.transactions_not_found else null))
    val uiState = _uiState.asStateFlow()
    private var previewJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            currencyPreference().collect { preference ->
                updateState { if (it.isEditMode) it else it.copy(targetCurrency = preference.currency,
                    currency = savedStateHandle.get<String>("currency") ?: preference.currency) }
            }
        }
        if (selectionInitialized) persistSelection(restoredAccountId)
        viewModelScope.launch {
            getAccounts().collect { result ->
                synchronized(stateLock) {
                    val state = _uiState.value
                    val signedOut = result is Resource.Error && result.cause.isSignInRequired()
                    if (signedOut) hasAccountData = false
                    else if (result !is Resource.Loading && result.data != null) hasAccountData = true
                    val accounts = when {
                        signedOut -> result.data.orEmpty()
                        result is Resource.Loading -> state.accounts
                        else -> result.data ?: state.accounts
                    }
                    _uiState.value = initializeAccountSelection(state.copy(
                        accounts = accounts, isAccountsLoading = result is Resource.Loading,
                        accountsError = if (result is Resource.Error) {
                            if (signedOut) R.string.transactions_sign_in else R.string.transactions_accounts_load_error
                        } else null
                    ))
                }
            }
        }
        viewModelScope.launch {
            getTransactionTags().collect { tags -> updateState { it.copy(tags = tags) } }
        }
        if (id != null) viewModelScope.launch {
            getTransaction(id).collect { result ->
                synchronized(stateLock) {
                    val state = _uiState.value
                    val next = when (result) {
                        is Resource.Loading -> state.copy(isLoading = true, error = null)
                        is Resource.Success -> state.copy(
                            isLoading = false, transaction = result.data,
                            error = if (result.data == null) R.string.transactions_not_found else null
                        )
                        is Resource.Error -> state.copy(
                            isLoading = false, transaction = if (result.cause.isSignInRequired()) result.data else result.data ?: state.transaction,
                            error = if (result.cause.isSignInRequired()) R.string.transactions_sign_in
                            else R.string.transactions_load_error
                        )
                    }
                    _uiState.value = initializeAccountSelection(next.copy(
                        currency = savedStateHandle.get<String>("currency") ?: next.transaction?.currency ?: next.currency,
                        targetCurrency = next.transaction?.targetCurrency ?: next.targetCurrency))
                }
            }
        }
    }

    // Called with stateLock held; once chosen, even a missing account requires an explicit replacement.
    private fun initializeAccountSelection(state: TransactionEditState): TransactionEditState {
        var next = state
        if (!selectionInitialized && hasAccountData && !state.isAccountsLoading && !state.isLoading &&
            (!state.isEditMode || state.transaction != null)) {
            val accountId = state.transaction?.accountId ?: state.accounts.firstOrNull { it.isDefault }?.id
            if (accountId != null) {
                persistSelection(accountId)
                next = state.copy(selectedAccountId = accountId, accountError = null)
            }
        }
        return when {
            next.selectedAccountId == null || next.isAccountsLoading || !hasAccountData -> next
            next.accounts.none { it.id == next.selectedAccountId } -> next.copy(accountError = R.string.transactions_account_unavailable)
            else -> next.copy(accountError = null)
        }
    }

    private fun persistSelection(accountId: String?) {
        selectionInitialized = true
        savedStateHandle["selected_account_id"] = accountId
        savedStateHandle["account_selection_initialized"] = true
    }

    private fun updateState(transform: (TransactionEditState) -> TransactionEditState) = synchronized(stateLock) {
        _uiState.value = transform(_uiState.value)
    }

    fun onAccountSelected(accountId: String) {
        synchronized(stateLock) {
            val state = _uiState.value
            if (state.isSaving || state.isSuccess || state.accounts.none { it.id == accountId }) return
            persistSelection(accountId)
            _uiState.value = state.copy(selectedAccountId = accountId, accountError = null)
        }
    }

    fun save(type: TransactionType, amountText: String, category: String, note: String, dateEpochMillis: Long) {
        val transaction = synchronized(stateLock) {
            val state = _uiState.value
            if (state.isSaving || state.isSuccess || state.isLoading || state.isAccountsLoading) return
            if (state.isEditMode && state.transaction == null) {
                updateState { it.copy(error = R.string.transactions_not_found) }
                return
            }
            if (!state.canSaveWithAccount) {
                updateState { it.copy(accountError = when {
                    state.selectedAccountId != null -> R.string.transactions_account_unavailable
                    state.accounts.isEmpty() -> R.string.transactions_account_required
                    else -> R.string.transactions_account_select
                }) }
                return
            }
            val amount = MoneyUtils.parseMinor(amountText, state.currency).getOrNull()
            val amountError = when {
                amount == null -> R.string.transactions_amount_invalid
                amount <= 0 -> R.string.transactions_amount_positive
                else -> null
            }
            val categoryError = if (category.trim().length > 100) R.string.transactions_tag_long else null
            val noteError = if (note.trim().length > 2000) R.string.transactions_description_long else null
            updateState { it.copy(amountError = amountError, categoryError = categoryError, noteError = noteError, error = null) }
            if (amountError != null || categoryError != null || noteError != null) return
            val saved = (state.transaction ?: Transaction(amountMinor = checkNotNull(amount))).copy(
                type = type, amountMinor = checkNotNull(amount), category = category, note = note,
                currency = state.currency, targetCurrency = state.targetCurrency,
                dateEpochMillis = dateEpochMillis, accountId = state.selectedAccountId
            )
            updateState { it.copy(isSaving = true) }
            saved
        }
        viewModelScope.launch {
            val result = if (id == null) addTransaction(transaction) else updateTransaction(transaction)
            result.onSuccess { updateState { it.copy(isSaving = false, isSuccess = true) } }
                .onFailure { error -> updateState { it.copy(
                    isSaving = false,
                    error = when {
                        error is AccountException && error.error == AccountError.SIGN_IN_REQUIRED -> R.string.transactions_sign_in
                        error is TransactionException -> when (error.error) {
                            TransactionError.SIGN_IN_REQUIRED -> R.string.transactions_sign_in
                            TransactionError.ACCOUNT_REQUIRED -> R.string.transactions_account_select
                            TransactionError.ACCOUNT_NOT_FOUND -> R.string.transactions_account_unavailable
                        }
                        else -> R.string.transactions_save_error
                    }
                ) } }
        }
    }

    fun onCurrencySelected(currency: String) {
        if (currency !in com.example.clarity.domain.model.Currencies.supported || uiState.value.isSaving) return
        savedStateHandle["currency"] = currency
        previewJob?.cancel()
        updateState { it.copy(currency = currency, preview = null) }
    }

    fun preview(amountText: String) {
        previewJob?.cancel()
        val state = uiState.value
        val amount = MoneyUtils.parseMinor(amountText, state.currency).getOrNull()
        updateState { it.copy(preview = null, previewError = false, previewLoading = false) }
        if (amount == null || amount <= 0) return
        val historical = state.transaction?.takeIf { it.amountMinor == amount && it.currency == state.currency }
        if (historical?.conversion != null) {
            updateState { it.copy(preview = historical.conversion, previewError = historical.pendingConversion) }
            return
        }
        previewJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            updateState { it.copy(previewLoading = true) }
            try {
                val snapshot = previewConversion(amount, state.currency, state.targetCurrency)
                updateState { it.copy(preview = snapshot, previewLoading = false) }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                updateState { it.copy(previewLoading = false, previewError = true) }
            }
        }
    }
}
