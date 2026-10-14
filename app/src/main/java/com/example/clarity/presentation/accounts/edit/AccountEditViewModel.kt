package com.example.clarity.presentation.accounts.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.R
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.usecase.account.CreateAccountUseCase
import com.example.clarity.domain.usecase.account.GetAccountUseCase
import com.example.clarity.domain.usecase.account.UpdateAccountUseCase
import com.example.clarity.presentation.accounts.accountErrorResource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AccountEditViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getAccount: GetAccountUseCase,
    private val createAccount: CreateAccountUseCase,
    private val updateAccount: UpdateAccountUseCase
) : ViewModel() {
    private val id: String? = savedStateHandle["id"]
    private val draft = Account(
        id = savedStateHandle.get<String>("draft_id") ?: Account(name = "", bankName = "").id,
        name = "", bankName = ""
    )
    private var account: Account? = null
    private val _uiState = MutableStateFlow(AccountEditState(
        isEditMode = id != null, isLoading = true, canSave = false,
        name = savedStateHandle["name"] ?: "", bankName = savedStateHandle["bank_name"] ?: "",
        accountType = savedStateHandle["account_type"] ?: "",
        currency = savedStateHandle["currency"] ?: draft.currency,
        isDefault = savedStateHandle["is_default"] ?: false,
        isSuccess = savedStateHandle["saved"] ?: false
    ))
    val uiState = _uiState.asStateFlow()

    init {
        savedStateHandle["draft_id"] = draft.id
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
                    savedStateHandle["saved"] = committed
                    _uiState.update { it.copy(isLoading = false, canSave = true, isSuccess = committed) }
                } else if (loaded == null) {
                    _uiState.update { it.copy(isLoading = false, canSave = false, error = R.string.accounts_not_found) }
                } else {
                    val hasDraft = savedStateHandle.get<Boolean>("form_loaded") == true
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
                    error = accountErrorResource(error.message, R.string.accounts_open_error)) }
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
        savedStateHandle["name"] = state.name
        savedStateHandle["bank_name"] = state.bankName
        savedStateHandle["account_type"] = state.accountType
        savedStateHandle["currency"] = state.currency
        savedStateHandle["is_default"] = state.isDefault
        savedStateHandle["form_loaded"] = true
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
                savedStateHandle["saved"] = true
                _uiState.update { it.copy(isSaving = false, isSuccess = true) }
            }.onFailure { error ->
                val message = accountErrorResource(error.message, R.string.accounts_save_error)
                _uiState.update { it.copy(
                    isSaving = false,
                    nameError = message.takeIf { it == R.string.accounts_name_blank || it == R.string.accounts_name_duplicate },
                    currencyError = message.takeIf { it == R.string.accounts_currency_invalid },
                    error = message.takeUnless {
                        it == R.string.accounts_name_blank || it == R.string.accounts_name_duplicate || it == R.string.accounts_currency_invalid
                    }
                ) }
            }
        }
    }
}
