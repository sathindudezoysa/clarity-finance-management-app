package com.example.clarity.presentation.wallet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.*
import com.example.clarity.domain.usecase.wallet.*
import com.example.clarity.domain.util.MoneyUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CashWalletViewModel @Inject constructor(
    private val observeWallet: ObserveCashWalletUseCase,
    private val recordCash: RecordCashTransactionUseCase,
    private val topUp: TopUpCashWalletUseCase,
    private val configureTopUp: ConfigureWalletTopUpUseCase,
    private val adjustCash: AdjustCashBalanceUseCase,
    private val savedState: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val _uiState = MutableStateFlow(CashWalletState(
        action = if (savedState.get<Boolean>("review") == true) WalletAction.TOP_UP else WalletAction.EXPENSE,
        selectedBankId = savedState["source"] ?: "", amount = savedState["amount"] ?: "",
        showTransferReview = savedState["review"] ?: false
    ))
    val uiState = _uiState.asStateFlow()
    private val retry = MutableStateFlow(0)

    init {
        // Lifecycle-aware UI collection also stops the database observer when the screen is not visible.
        viewModelScope.launch {
            combine(_uiState.subscriptionCount.map { it > 0 }.distinctUntilChanged(), retry) { active, _ -> active }
                .collectLatest { active ->
                    if (active) observeWallet().catch {
                        _uiState.update { it.copy(isLoading = false, wallet = CashWallet(), error = "We couldn't load your wallet. Please try again.") }
                    }.collect { wallet ->
                        _uiState.update { previous ->
                            val restoredOwner: String? = savedState["owner"]
                            val changedOwner = previous.wallet.ownerId != null && previous.wallet.ownerId != wallet.ownerId
                            val state = if (changedOwner || (restoredOwner != null && restoredOwner != wallet.ownerId)) {
                                clearDraft(); CashWalletState()
                            } else previous
                            val activeAccounts = wallet.accounts.filter { it.isActive }
                            val selected = state.selectedBankId.takeIf { id -> activeAccounts.any { it.id == id } }
                                ?: activeAccounts.firstOrNull { it.isDefault }?.id ?: activeAccounts.firstOrNull()?.id.orEmpty()
                            val low = CashWalletRules.needsReminder(wallet.balanceMinor, wallet.topUpOption) &&
                                activeAccounts.any { it.id == wallet.topUpOption?.bankAccountId }
                            if (!low) savedState["dismissed"] = null as String?
                            state.copy(wallet = wallet, isLoading = false, selectedBankId = selected,
                                reminderVisible = low && savedState.get<String>("dismissed") != reminderKey(wallet))
                        }
                    }
                }
        }
    }

    fun retryLoading() { _uiState.update { it.copy(isLoading = true, error = null) }; retry.value++ }
    private fun edit(transform: (CashWalletState) -> CashWalletState) {
        if (!_uiState.value.isSaving) _uiState.update { transform(it).copy(error = null, message = null) }
    }
    fun selectAction(action: WalletAction) {
        if (_uiState.value.isSaving) return
        clearDraft()
        edit { state ->
            val option = state.wallet.topUpOption
            state.copy(action = action, amount = "", showAddCashChoices = false, showTransferReview = false,
                automaticEnabled = option?.enabled ?: false, automaticExecution = option?.automaticExecution ?: false,
                threshold = MoneyUtils.formatMinor(option?.thresholdMinor ?: 0),
                automaticAmount = option?.amountMinor?.let(MoneyUtils::formatMinor).orEmpty(),
                selectedBankId = option?.bankAccountId?.takeIf { id -> state.wallet.accounts.any { it.id == id && it.isActive } }
                    ?: state.selectedBankId)
        }
    }
    fun showAddCash(show: Boolean) = edit { it.copy(showAddCashChoices = show) }
    fun setAmount(value: String) = edit { it.copy(amount = value, showTransferReview = false) }
    fun setCategory(value: String) = edit { it.copy(category = value) }
    fun setNote(value: String) = edit { it.copy(note = value) }
    fun selectBank(value: String) = edit { it.copy(selectedBankId = value, showTransferReview = false) }
    fun setAutomatic(value: Boolean) = edit { it.copy(automaticEnabled = value) }
    fun setAutomaticExecution(value: Boolean) = edit { it.copy(automaticExecution = value) }
    fun setThreshold(value: String) = edit { it.copy(threshold = value) }
    fun setAutomaticAmount(value: String) = edit { it.copy(automaticAmount = value) }
    fun cancelReview() { if (!_uiState.value.isSaving) { clearDraft(); edit { it.copy(showTransferReview = false) } } }
    fun dismissReminder() {
        savedState["dismissed"] = reminderKey(_uiState.value.wallet)
        edit { it.copy(reminderVisible = false) }
    }
    fun reviewReminder() {
        val option = _uiState.value.wallet.topUpOption ?: return
        dismissReminder()
        selectAction(WalletAction.TOP_UP)
        edit { it.copy(selectedBankId = option.bankAccountId, amount = MoneyUtils.formatMinor(option.amountMinor)) }
        reviewTransfer()
    }

    fun reviewTransfer() {
        if (_uiState.value.isSaving || _uiState.value.isLoading) return
        try {
            val state = _uiState.value
            val amount = parseMoney(state.amount)
            walletRequire(amount > 0, "Please enter an amount greater than zero.")
            val bank = state.wallet.accounts.firstOrNull { it.id == state.selectedBankId && it.isActive }
            walletRequire(bank != null, "Choose a bank account to continue.")
            walletRequire(requireNotNull(bank).balanceMinor >= BigInteger.valueOf(amount), "Your bank balance is not enough for this top-up.")
            walletRequire(state.wallet.balanceMinor + BigInteger.valueOf(amount) <= BigInteger.valueOf(Long.MAX_VALUE), "The wallet balance is too large. Use a smaller amount.")
            savedState["operation"] = UUID.randomUUID().toString()
            savedState["owner"] = state.wallet.ownerId
            savedState["source"] = state.selectedBankId
            savedState["amount"] = state.amount
            savedState["review"] = true
            edit { it.copy(showTransferReview = true) }
        } catch (error: WalletValidationException) { _uiState.update { it.copy(error = error.message) } }
    }

    fun save() {
        if (_uiState.value.action == WalletAction.TOP_UP) { reviewTransfer(); return }
        val state = _uiState.value
        perform {
            val result = when (state.action) {
                WalletAction.EXPENSE, WalletAction.INCOME -> recordCash(parseMoney(state.amount),
                    if (state.action == WalletAction.EXPENSE) TransactionType.EXPENSE else TransactionType.INCOME, state.category, state.note)
                WalletAction.AUTOMATIC -> configureTopUp(WalletTopUpOption(state.selectedBankId, state.automaticEnabled,
                    if (state.automaticEnabled) parseMoney(state.threshold) else 0,
                    if (state.automaticEnabled) parseMoney(state.automaticAmount) else 0, state.automaticExecution))
                WalletAction.ADJUSTMENT -> adjustCash(parseMoney(state.amount), state.note, UUID.randomUUID().toString())
                WalletAction.TOP_UP -> error("Use transfer review")
            }
            result.getOrThrow()
            _uiState.update { it.copy(amount = "", category = "", note = "", message = "Saved") }
        }
    }
    fun confirmTransfer() {
        val state = _uiState.value
        if (!state.showTransferReview) return
        val operation: String = savedState["operation"] ?: return
        perform {
            topUp(state.selectedBankId, parseMoney(state.amount), operation).getOrThrow()
            clearDraft()
            _uiState.update { it.copy(showTransferReview = false, amount = "", message = "Top-up recorded") }
        }
    }
    private fun perform(block: suspend () -> Unit) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, error = null, message = null) }
        viewModelScope.launch {
            try { block() }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(error = if (error is WalletValidationException) error.message else "We couldn't save your changes. Please try again.") }
            } finally { _uiState.update { it.copy(isSaving = false) } }
        }
    }
    private fun clearDraft() {
        listOf("operation", "owner", "source", "amount", "review").forEach { savedState.remove<Any>(it) }
    }
    private fun reminderKey(wallet: CashWallet) = "${wallet.ownerId}:${wallet.topUpOption}"
}

internal fun parseMoney(text: String): Long {
    if (text.isBlank()) throw WalletValidationException("Please enter an amount.")
    return MoneyUtils.parseMinor(text).getOrElse { throw WalletValidationException("Enter a valid amount with up to two decimal places.") }
}
