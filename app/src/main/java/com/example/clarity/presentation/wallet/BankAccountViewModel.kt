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
import java.util.UUID
import javax.inject.Inject

data class BankAccountState(
    val account: WalletBankAccount? = null, val bankId: String = "other", val nickname: String = "",
    val lastFour: String = "", val type: String = "SAVINGS", val opening: String = "0.00",
    val isDefault: Boolean = false, val isLoading: Boolean = true, val isSaving: Boolean = false,
    val saved: Boolean = false, val error: String? = null, val activity: List<WalletActivity> = emptyList()
)

@HiltViewModel
class BankAccountViewModel @Inject constructor(
    private val observeWallet: ObserveCashWalletUseCase,
    private val saveAccount: SaveWalletBankAccountUseCase,
    private val archiveAccount: ArchiveWalletBankAccountUseCase,
    private val getActivity: GetWalletBankActivityUseCase,
    private val savedState: SavedStateHandle
) : ViewModel() {
    val editingId: String? = savedState["accountId"]
    private val accountId = editingId ?: savedState.get<String>("newId") ?: UUID.randomUUID().toString().also { savedState["newId"] = it }
    private val _state = MutableStateFlow(BankAccountState(bankId = savedState["bankId"] ?: "other"))
    val uiState = _state.asStateFlow()
    private var loaded = false
    private var ownerId: String? = null
    private var sessionValid = true

    init {
        viewModelScope.launch {
            _state.subscriptionCount.map { it > 0 }.distinctUntilChanged().collectLatest { active ->
                if (active) observeWallet().catch { _state.update { it.copy(isLoading = false, error = "We couldn't load this account. Go back and try again.") } }
                    .collect { wallet ->
                        // No authenticated snapshot yet: an empty initial value is not a missing account.
                        if (ownerId == null && wallet.ownerId == null) return@collect
                        if (ownerId != null && ownerId != wallet.ownerId) {
                            sessionValid = false
                            _state.value = BankAccountState(isLoading = false, error = "Your session changed. Go back and sign in again.")
                            return@collect
                        }
                        ownerId = wallet.ownerId
                        val existing = wallet.accounts.firstOrNull { it.id == editingId }
                        if (editingId != null && existing == null) {
                            _state.update { it.copy(account = null, isLoading = false, error = "This account is not available.") }
                            return@collect
                        } else if (!loaded) {
                            val bank = savedState.get<String>("bankId")?.takeIf { BankCatalog.find(it) != null } ?: existing?.bankId ?: "other"
                            _state.update { it.copy(account = existing, bankId = bank,
                                nickname = savedState["nickname"] ?: existing?.name.orEmpty(), lastFour = savedState["lastFour"] ?: existing?.lastFour.orEmpty(),
                                type = savedState["type"] ?: existing?.accountType ?: "SAVINGS", opening = existing?.let { MoneyUtils.formatMinor(it.openingBalanceMinor) } ?: savedState["opening"] ?: "0.00",
                                isDefault = savedState["default"] ?: existing?.isDefault ?: false, isLoading = false, error = null) }
                            loaded = true
                        } else _state.update { it.copy(account = existing, isLoading = false, error = null) }
                        if (editingId != null) getActivity(editingId).onSuccess { activity -> _state.update { it.copy(activity = activity) } }
                    }
            }
        }
    }

    private fun edit(key: String, value: Any, block: (BankAccountState) -> BankAccountState) {
        if (!_state.value.isSaving) { savedState[key] = value; _state.update { block(it).copy(error = null) } }
    }
    fun nickname(value: String) = edit("nickname", value) { it.copy(nickname = value) }
    fun lastFour(value: String) = edit("lastFour", value) { it.copy(lastFour = value) }
    fun type(value: String) = edit("type", value) { it.copy(type = value) }
    fun opening(value: String) {
        if (editingId == null) edit("opening", value) { it.copy(opening = value) }
    }
    fun default(value: Boolean) = edit("default", value) { it.copy(isDefault = value) }
    fun chooseBank(id: String) {
        if (BankCatalog.find(id) != null) edit("bankId", id) { it.copy(bankId = id) }
    }

    fun save() = perform {
        walletRequire(sessionValid && ownerId != null && (editingId == null || _state.value.account != null), "Please sign in and choose an available account.")
        val state = _state.value
        saveAccount(WalletBankAccountInput(accountId, state.bankId, state.nickname, state.lastFour, state.type,
            state.account?.openingBalanceMinor ?: parseMoney(state.opening), isDefault = state.isDefault)).getOrThrow()
    }
    fun archive() = perform {
        walletRequire(sessionValid && ownerId != null, "Your session changed. Go back and sign in again.")
        archiveAccount(requireNotNull(editingId)).getOrThrow()
    }
    private fun perform(block: suspend () -> Unit) {
        if (_state.value.isSaving || _state.value.isLoading) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try { block(); _state.update { it.copy(saved = true) } }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.update { it.copy(error = if (error is WalletValidationException) error.message else "We couldn't save this account. Please try again.") }
            } finally { _state.update { it.copy(isSaving = false) } }
        }
    }
}
