package com.example.clarity.presentation.accounts.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.usecase.account.CreateAccountUseCase
import com.example.clarity.domain.usecase.account.GetAccountUseCase
import com.example.clarity.domain.usecase.account.UpdateAccountUseCase
import com.example.clarity.presentation.accounts.AccountMessages
import com.example.clarity.presentation.accounts.accountErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AccountEditViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getAccount: GetAccountUseCase,
    private val createAccount: CreateAccountUseCase,
    private val updateAccount: UpdateAccountUseCase
) : ViewModel() {
    private val id: String? = savedStateHandle["id"]
    private val draftId = savedStateHandle.get<String>(KEY_DRAFT_ID)
        ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_DRAFT_ID] = it }
    private val draft = Account(
        id = draftId,
        name = "", bankName = ""
    )
    private var account: Account? = null
    private val _uiState = MutableStateFlow(AccountEditState(
        isEditMode = id != null, isLoading = true, canSave = false,
        name = savedStateHandle[KEY_NAME] ?: "", bankName = savedStateHandle[KEY_BANK_NAME] ?: "",
        accountType = savedStateHandle[KEY_ACCOUNT_TYPE] ?: "",
        currency = savedStateHandle[KEY_CURRENCY] ?: draft.currency,
        isDefault = savedStateHandle[KEY_IS_DEFAULT] ?: false,
        isSuccess = savedStateHandle[KEY_SAVED] ?: false
    ))
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val result = getAccount(id ?: draft.id)
            result.onSuccess { loaded ->
                account = loaded
                if (id == null) {
                    // A commit may have completed just before process death. Do not create it twice.
                    val committed = loaded != null || _uiState.value.isSuccess
                    savedStateHandle[KEY_SAVED] = committed
                    _uiState.update { it.copy(isLoading = false, canSave = true, isSuccess = committed) }
                } else if (loaded == null) {
                    _uiState.update { it.copy(isLoading = false, canSave = false, error = AccountMessages.NOT_FOUND) }
                } else {
                    val hasDraft = savedStateHandle.get<Boolean>(KEY_FORM_LOADED) == true
                    _uiState.update { state -> state.copy(
                        isLoading = false, canSave = true,
                        name = if (hasDraft) state.name else loaded.name,
                        bankName = if (hasDraft) state.bankName else loaded.bankName,
                        accountType = if (hasDraft) state.accountType else loaded.accountType.orEmpty(),
                        currency = if (hasDraft) state.currency else loaded.currency,
                        isDefault = loaded.isDefault || (if (hasDraft) state.isDefault else false),
                        isDefaultLocked = loaded.isDefault
                    ) }
                    persistForm()
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, canSave = false,
                    error = accountErrorMessage(error, AccountMessages.OPEN_ERROR)) }
            }
        }
    }

    fun onNameChange(value: String) = change { it.copy(name = value, nameError = null, error = null) }
    fun onBankNameChange(value: String) = change { it.copy(bankName = value, error = null) }
    fun onTypeChange(value: String) = change { it.copy(accountType = value, error = null) }
    fun onCurrencyChange(value: String) = change { it.copy(currency = value, currencyError = null, error = null) }
    fun onDefaultChange(value: Boolean) {
        if (!_uiState.value.isDefaultLocked) change { it.copy(isDefault = value, error = null) }
    }

    private fun change(transform: (AccountEditState) -> AccountEditState) {
        val state = _uiState.value
        if (state.isLoading || state.isSaving || state.isSuccess || !state.canSave) return
        _uiState.update(transform)
        persistForm()
    }

    private fun persistForm() {
        val state = _uiState.value
        savedStateHandle[KEY_NAME] = state.name
        savedStateHandle[KEY_BANK_NAME] = state.bankName
        savedStateHandle[KEY_ACCOUNT_TYPE] = state.accountType
        savedStateHandle[KEY_CURRENCY] = state.currency
        savedStateHandle[KEY_IS_DEFAULT] = state.isDefault
        savedStateHandle[KEY_FORM_LOADED] = true
    }

    fun save() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving || state.isSuccess || !state.canSave) return
        val edited = (account ?: draft).copy(
            name = state.name, bankName = state.bankName,
            accountType = state.accountType.trim().takeIf { it.isNotEmpty() },
            currency = state.currency.trim().uppercase(Locale.ROOT), isDefault = state.isDefault
        )
        _uiState.update { it.copy(isSaving = true, nameError = null, currencyError = null, error = null) }
        viewModelScope.launch {
            val result = if (id == null) createAccount(edited) else updateAccount(edited)
            result.onSuccess {
                savedStateHandle[KEY_SAVED] = true
                _uiState.update { it.copy(isSaving = false, isSuccess = true) }
            }.onFailure { error ->
                val message = accountErrorMessage(error, AccountMessages.SAVE_ERROR)
                _uiState.update { it.copy(
                    isSaving = false,
                    nameError = message.takeIf { it == AccountMessages.NAME_BLANK || it == AccountMessages.NAME_DUPLICATE },
                    currencyError = message.takeIf { it == AccountMessages.CURRENCY_INVALID },
                    error = message.takeUnless {
                        it == AccountMessages.NAME_BLANK || it == AccountMessages.NAME_DUPLICATE || it == AccountMessages.CURRENCY_INVALID
                    }
                ) }
            }
        }
    }
}

private const val KEY_NAME = "account_edit_name"
private const val KEY_BANK_NAME = "account_edit_bank_name"
private const val KEY_ACCOUNT_TYPE = "account_edit_account_type"
private const val KEY_CURRENCY = "account_edit_currency"
private const val KEY_IS_DEFAULT = "account_edit_is_default"
private const val KEY_FORM_LOADED = "account_edit_form_loaded"
private const val KEY_SAVED = "account_edit_saved"
private const val KEY_DRAFT_ID = "account_edit_draft_id"
