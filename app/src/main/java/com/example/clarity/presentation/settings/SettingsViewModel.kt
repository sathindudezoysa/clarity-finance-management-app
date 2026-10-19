package com.example.clarity.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val observeCurrencyPreference: com.example.clarity.domain.usecase.currency.ObserveCurrencyPreferenceUseCase,
    private val setCurrencyPreference: com.example.clarity.domain.usecase.currency.SetCurrencyPreferenceUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch { observeCurrencyPreference().collect { preference ->
            _uiState.update { it.copy(currency = preference.currency, pendingSync = preference.pendingSync) }
        } }
    }
    fun setCurrency(currency: String) {
        if (_uiState.value.saving) return
        _uiState.update { it.copy(saving = true, error = false) }
        viewModelScope.launch {
            val result = setCurrencyPreference(currency)
            _uiState.update { it.copy(saving = false, error = result.isFailure) }
        }
    }
    fun onBack() { _uiState.update { it.copy(destination = SettingsDestination.BACK) } }
    fun onBankAccounts() { _uiState.update { it.copy(destination = SettingsDestination.BANK_ACCOUNTS) } }
    fun onNavigationHandled() { _uiState.update { it.copy(destination = null) } }
}
