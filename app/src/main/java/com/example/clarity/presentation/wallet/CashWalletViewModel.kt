package com.example.clarity.presentation.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.model.WalletTopUpOption
import com.example.clarity.domain.usecase.wallet.*
import com.example.clarity.domain.util.MoneyUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CashWalletViewModel @Inject constructor(
    observeWallet: ObserveCashWalletUseCase,
    private val recordCash: RecordCashTransactionUseCase,
    private val topUp: TopUpCashWalletUseCase,
    private val addBank: AddWalletBankAccountUseCase,
    private val configureTopUp: ConfigureWalletTopUpUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(CashWalletState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeWallet().catch { error ->
                _uiState.update { it.copy(isLoading = false, error = error.message ?: "Unable to load wallet") }
            }.collect { wallet ->
                _uiState.update {
                    it.copy(wallet = wallet, isLoading = false, selectedBankId =
                        it.selectedBankId.takeIf { id -> wallet.accounts.any { bank -> bank.id == id } }
                            ?: wallet.topUpOption?.bankAccountId ?: wallet.accounts.firstOrNull()?.id.orEmpty())
                }
            }
        }
    }

    private fun edit(transform: (CashWalletState) -> CashWalletState) {
        if (!_uiState.value.isSaving) _uiState.update { transform(it).copy(error = null, message = null) }
    }
    fun selectAction(action: WalletAction) = edit { state ->
        val option = state.wallet.topUpOption
        state.copy(action = action, amount = "", automaticEnabled = option?.enabled ?: true,
            threshold = MoneyUtils.formatMinor(option?.thresholdMinor ?: 0),
            automaticAmount = option?.amountMinor?.let(MoneyUtils::formatMinor).orEmpty(),
            selectedBankId = option?.bankAccountId ?: state.selectedBankId)
    }
    fun setAmount(value: String) = edit { it.copy(amount = value) }
    fun setCategory(value: String) = edit { it.copy(category = value) }
    fun setNote(value: String) = edit { it.copy(note = value) }
    fun selectBank(value: String) = edit { it.copy(selectedBankId = value) }
    fun setAutomatic(value: Boolean) = edit { it.copy(automaticEnabled = value) }
    fun setThreshold(value: String) = edit { it.copy(threshold = value) }
    fun setAutomaticAmount(value: String) = edit { it.copy(automaticAmount = value) }
    fun showBankForm(show: Boolean) = edit { it.copy(showBankForm = show, bankName = "", bankOpeningBalance = "0.00") }
    fun setBankName(value: String) = edit { it.copy(bankName = value) }
    fun setBankBalance(value: String) = edit { it.copy(bankOpeningBalance = value) }

    fun save() {
        val state = _uiState.value
        perform {
            val result = when (state.action) {
                WalletAction.EXPENSE, WalletAction.INCOME -> recordCash(MoneyUtils.parseMinor(state.amount).getOrThrow(),
                    if (state.action == WalletAction.EXPENSE) TransactionType.EXPENSE else TransactionType.INCOME,
                    state.category, state.note)
                WalletAction.TOP_UP -> topUp(state.selectedBankId, MoneyUtils.parseMinor(state.amount).getOrThrow())
                WalletAction.AUTOMATIC -> configureTopUp(WalletTopUpOption(state.selectedBankId, state.automaticEnabled,
                    MoneyUtils.parseMinor(state.threshold).getOrThrow(), MoneyUtils.parseMinor(state.automaticAmount).getOrThrow()))
            }
            result.getOrThrow()
            _uiState.update { it.copy(amount = "", category = "", note = "", message = "Saved") }
        }
    }

    fun saveBank() {
        val state = _uiState.value
        perform {
            addBank(state.bankName, MoneyUtils.parseMinor(state.bankOpeningBalance).getOrThrow()).getOrThrow()
            _uiState.update { it.copy(showBankForm = false, bankName = "", message = "Bank account added") }
        }
    }

    private fun perform(block: suspend () -> Unit) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, error = null, message = null) }
        viewModelScope.launch {
            try { block() }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(error = error.message ?: "Unable to save. Please try again.") }
            } finally { _uiState.update { it.copy(isSaving = false) } }
        }
    }
}
