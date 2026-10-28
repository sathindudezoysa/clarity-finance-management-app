package com.example.clarity.presentation.transactions.edit

import com.example.clarity.presentation.navigation.transactionIdArgument
import com.example.clarity.presentation.navigation.TRANSACTION_ID_ARGUMENT
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.presentation.transactions.TransactionMessages
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.AccountError
import com.example.clarity.domain.model.AccountException
import com.example.clarity.domain.model.ProofOpenFile
import com.example.clarity.domain.model.ProofPolicy
import com.example.clarity.domain.model.ProofStoragePlanException
import com.example.clarity.domain.model.TransactionError
import com.example.clarity.domain.model.TransactionException
import com.example.clarity.domain.model.TransactionTags
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.usecase.transaction.AddTransactionTagUseCase
import com.example.clarity.domain.usecase.transaction.AddTransactionUseCase
import com.example.clarity.domain.usecase.transaction.AttachTransactionProofUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionTagsUseCase
import com.example.clarity.domain.usecase.transaction.ObserveTransactionProofsUseCase
import com.example.clarity.domain.usecase.transaction.OpenTransactionProofUseCase
import com.example.clarity.domain.usecase.transaction.RefreshTransactionProofsUseCase
import com.example.clarity.domain.usecase.transaction.RemoveUnavailableProofUseCase
import com.example.clarity.domain.usecase.transaction.UpdateTransactionUseCase
import com.example.clarity.domain.usecase.transaction.VerifyReceiptAmountUseCase
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.util.MoneyUtils
import com.example.clarity.domain.util.Resource
import com.example.clarity.domain.util.isSignInRequired
import com.example.clarity.presentation.transactions.proofs.ProofMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
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

    private var observeProofs: ObserveTransactionProofsUseCase? = null
    private var attachProof: AttachTransactionProofUseCase? = null
    private var openProof: OpenTransactionProofUseCase? = null
    private var verifyProof: VerifyReceiptAmountUseCase? = null
    private var refreshProofs: RefreshTransactionProofsUseCase? = null
    private var removeUnavailableProof: RemoveUnavailableProofUseCase? = null
    private var addTransactionTag: AddTransactionTagUseCase? = null

    @Inject
    internal fun injectProofUseCases(
        observeProofs: ObserveTransactionProofsUseCase,
        attachProof: AttachTransactionProofUseCase,
        openProof: OpenTransactionProofUseCase,
        verifyProof: VerifyReceiptAmountUseCase,
        refreshProofs: RefreshTransactionProofsUseCase,
        removeUnavailableProof: RemoveUnavailableProofUseCase,
        addTransactionTag: AddTransactionTagUseCase
    ) {
        this.observeProofs = observeProofs
        this.attachProof = attachProof
        this.openProof = openProof
        this.verifyProof = verifyProof
        this.refreshProofs = refreshProofs
        this.removeUnavailableProof = removeUnavailableProof
        this.addTransactionTag = addTransactionTag
        if (id != null) {
            viewModelScope.launch {
                observeProofs.invoke(id).catch { error ->
                    if (error is CancellationException) throw error
                    updateState { it.copy(proofError = ProofMessages.LOAD_ERROR) }
                }.collect { rows ->
                    updateState { it.copy(proofs = rows) }
                }
            }
            viewModelScope.launch {
                refreshProofs.invoke(id).onFailure { err ->
                    updateState {
                        it.copy(
                            proofError = if (err is ProofStoragePlanException) {
                                ProofMessages.STORAGE_PLAN_REQUIRED
                            } else {
                                ProofMessages.SYNC_ERROR
                            }
                        )
                    }
                }
            }
        }
    }

    private val isEditMode = savedStateHandle.get<Any?>(TRANSACTION_ID_ARGUMENT) != null
    private val id = savedStateHandle.transactionIdArgument()
    private val restoredAccountId = savedStateHandle.get<Any?>("selected_account_id") as? String
    private val stateLock = Any()
    private var hasAccountData = false
    private var selectionInitialized = savedStateHandle.get<Any?>("account_selection_initialized") == true || restoredAccountId != null
    private val _uiState = MutableStateFlow(TransactionEditState(isEditMode = isEditMode, isLoading = id != null,
        selectedAccountId = restoredAccountId,
        error = if (isEditMode && id == null) TransactionMessages.NOT_FOUND else null))
    val uiState = _uiState.asStateFlow()
    private val _openFiles = Channel<ProofOpenFile>(Channel.BUFFERED)
    val openFiles = _openFiles.receiveAsFlow()
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
                            if (signedOut) TransactionMessages.SIGN_IN else TransactionMessages.ACCOUNTS_LOAD_ERROR
                        } else null
                    ))
                }
            }
        }
        viewModelScope.launch {
            getTransactionTags().collect { tags -> updateState { it.copy(tags = tags) } }
        }
        viewModelScope.launch {
            getTransactionTags(TransactionType.EXPENSE).collect { expenseTags ->
                updateState { it.copy(expenseTags = expenseTags) }
            }
        }
        viewModelScope.launch {
            getTransactionTags(TransactionType.INCOME).collect { incomeTags ->
                updateState { it.copy(incomeTags = incomeTags) }
            }
        }
        if (id != null) {
            viewModelScope.launch {
                getTransaction(id).collect { result ->
                    synchronized(stateLock) {
                        val state = _uiState.value
                        val next = when (result) {
                            is Resource.Loading -> state.copy(isLoading = true, error = null)
                            is Resource.Success -> state.copy(
                                isLoading = false, transaction = result.data,
                                error = if (result.data == null) TransactionMessages.NOT_FOUND else null
                            )
                            is Resource.Error -> state.copy(
                                isLoading = false, transaction = if (result.cause.isSignInRequired()) result.data else result.data ?: state.transaction,
                                error = if (result.cause.isSignInRequired()) TransactionMessages.SIGN_IN
                                else TransactionMessages.LOAD_ERROR
                            )
                        }
                        _uiState.value = initializeAccountSelection(next.copy(
                            currency = savedStateHandle.get<String>("currency") ?: next.transaction?.currency ?: next.currency,
                            targetCurrency = next.transaction?.targetCurrency ?: next.targetCurrency))
                    }
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
            next.accounts.none { it.id == next.selectedAccountId } -> next.copy(accountError = TransactionMessages.ACCOUNT_UNAVAILABLE)
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
                updateState { it.copy(error = TransactionMessages.NOT_FOUND) }
                return
            }
            if (!state.canSaveWithAccount) {
                updateState { it.copy(accountError = when {
                    state.selectedAccountId != null -> TransactionMessages.ACCOUNT_UNAVAILABLE
                    state.accounts.isEmpty() -> TransactionMessages.ACCOUNT_REQUIRED
                    else -> TransactionMessages.ACCOUNT_SELECT
                }) }
                return
            }
            val amount = MoneyUtils.parseMinor(amountText, state.currency).getOrNull()
            val amountError = when {
                amount == null -> TransactionMessages.AMOUNT_INVALID
                amount <= 0 -> TransactionMessages.AMOUNT_POSITIVE
                else -> null
            }
            val categoryError = if (category.trim().length > 100) TransactionMessages.TAG_LONG else null
            val noteError = if (note.trim().length > 2000) TransactionMessages.DESCRIPTION_LONG else null
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
            result.onSuccess {
                if (id == null) {
                    val drafts = synchronized(stateLock) { _uiState.value.pendingProofs }
                    val attach = attachProof
                    val observe = observeProofs
                    val verify = verifyProof
                    if (drafts.isNotEmpty() && attach != null) {
                        var attachError: String? = null
                        for (draft in drafts) {
                            attach.invoke(transaction.id, draft.uri).onFailure {
                                attachError = ProofMessages.ATTACH_ERROR
                            }
                        }
                        if (drafts.any { it.verified } && observe != null && verify != null) {
                            runCatching {
                                val attached = observe.invoke(transaction.id).first()
                                attached.forEach { proof ->
                                    verify.invoke(transaction.id, proof.id, transaction.amountMinor, true)
                                }
                            }
                        }
                        refreshProofs?.invoke(transaction.id)
                        if (attachError != null) {
                            updateState { it.copy(proofError = attachError) }
                        }
                    }
                }
                updateState { it.copy(isSaving = false, isSuccess = true) }
            }.onFailure { error -> updateState { it.copy(
                    isSaving = false,
                    error = when {
                        error is AccountException && error.error == AccountError.SIGN_IN_REQUIRED -> TransactionMessages.SIGN_IN
                        error is TransactionException -> when (error.error) {
                            TransactionError.SIGN_IN_REQUIRED -> TransactionMessages.SIGN_IN
                            TransactionError.ACCOUNT_REQUIRED -> TransactionMessages.ACCOUNT_SELECT
                            TransactionError.ACCOUNT_NOT_FOUND -> TransactionMessages.ACCOUNT_UNAVAILABLE
                            TransactionError.INSUFFICIENT_BALANCE -> TransactionMessages.INSUFFICIENT_BALANCE
                        }
                        else -> TransactionMessages.SAVE_ERROR
                    }
                ) } }
        }
    }

    fun onAttachProof(uri: String, displayName: String = "Receipt slip") {
        val currentState = _uiState.value
        if (currentState.isSaving || currentState.isProofBusy) return
        val attach = attachProof
        if (id != null && attach != null) {
            runProofAction(ProofMessages.ATTACH_ERROR) {
                attach.invoke(id, uri).getOrThrow()
                refreshProofs?.invoke(id)?.onFailure { err ->
                    updateState {
                        it.copy(
                            proofError = if (err is ProofStoragePlanException) {
                                ProofMessages.STORAGE_PLAN_REQUIRED
                            } else {
                                ProofMessages.SYNC_ERROR
                            }
                        )
                    }
                }
            }
        } else {
            updateState { state ->
                if (state.pendingProofs.size >= ProofPolicy.MAX_ATTACHMENTS) {
                    state.copy(proofError = ProofMessages.ATTACH_ERROR)
                } else {
                    val cleanName = displayName.trim().ifBlank { "Receipt_${state.pendingProofs.size + 1}.jpg" }
                    state.copy(
                        pendingProofs = state.pendingProofs + PendingProofDraft(uri = uri, displayName = cleanName, verified = true),
                        proofError = ProofMessages.STORAGE_PLAN_REQUIRED
                    )
                }
            }
        }
    }

    fun onRemovePendingProof(uri: String) {
        updateState { state ->
            state.copy(pendingProofs = state.pendingProofs.filterNot { it.uri == uri }, proofError = null)
        }
    }

    fun onTogglePendingProofVerified(uri: String) {
        updateState { state ->
            state.copy(
                pendingProofs = state.pendingProofs.map {
                    if (it.uri == uri) it.copy(verified = !it.verified) else it
                }
            )
        }
    }

    fun onOpenProof(proofId: String) {
        val txId = id ?: return
        val opener = openProof ?: return
        runProofAction(ProofMessages.OPEN_ERROR) {
            _openFiles.send(opener.invoke(txId, proofId).getOrThrow())
        }
    }

    fun onVerifyProof(proofId: String, amountMinor: Long, verified: Boolean) {
        val txId = id ?: return
        val verifier = verifyProof ?: return
        runProofAction(ProofMessages.VERIFY_ERROR) {
            verifier.invoke(txId, proofId, amountMinor, verified).getOrThrow()
            refreshProofs?.invoke(txId)?.onFailure { err ->
                updateState {
                    it.copy(
                        proofError = if (err is ProofStoragePlanException) {
                            ProofMessages.STORAGE_PLAN_REQUIRED
                        } else {
                            ProofMessages.SYNC_ERROR
                        }
                    )
                }
            }
        }
    }

    fun onRemoveUnavailableProof(proofId: String) {
        val txId = id ?: return
        val remover = removeUnavailableProof ?: return
        runProofAction(ProofMessages.REMOVE_ERROR) {
            remover.invoke(txId, proofId).getOrThrow()
        }
    }

    fun onRetryProofs() {
        val txId = id ?: return
        val refresher = refreshProofs ?: return
        runProofAction(ProofMessages.SYNC_ERROR) {
            refresher.invoke(txId).getOrThrow()
        }
    }

    fun viewerUnavailable() {
        updateState { it.copy(proofError = ProofMessages.VIEWER_ERROR) }
    }

    private fun runProofAction(errorMessage: String, block: suspend () -> Unit) {
        if (_uiState.value.isProofBusy) return
        updateState { it.copy(isProofBusy = true, proofError = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val msg = if (error is ProofStoragePlanException) ProofMessages.STORAGE_PLAN_REQUIRED else errorMessage
                updateState { it.copy(proofError = msg) }
            } finally {
                updateState { it.copy(isProofBusy = false) }
            }
        }
    }

    fun onCurrencySelected(currency: String) {
        if (currency !in com.example.clarity.domain.model.Currencies.supported || uiState.value.isSaving) return
        savedStateHandle["currency"] = currency
        previewJob?.cancel()
        updateState { it.copy(currency = currency, preview = null) }
    }

    fun onAddCustomTag(name: String, type: TransactionType) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        if (clean.length > 100) {
            updateState { it.copy(categoryError = TransactionMessages.TAG_LONG) }
            return
        }
        updateState { state ->
            val updatedExpense = if (type == TransactionType.EXPENSE) {
                (state.expenseTags + clean).distinctBy { TransactionTags.key(it) }
            } else state.expenseTags
            val updatedIncome = if (type == TransactionType.INCOME) {
                (state.incomeTags + clean).distinctBy { TransactionTags.key(it) }
            } else state.incomeTags
            val updatedAll = (state.tags + clean).distinctBy { TransactionTags.key(it) }
            state.copy(
                tags = updatedAll,
                expenseTags = updatedExpense,
                incomeTags = updatedIncome,
                categoryError = null
            )
        }
        viewModelScope.launch {
            addTransactionTag?.invoke(clean, type)
        }
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
