package com.example.clarity.presentation.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsState())
    val uiState = _uiState.asStateFlow()

    fun onBack() { _uiState.value = SettingsState(SettingsDestination.BACK) }
    fun onBankAccounts() { _uiState.value = SettingsState(SettingsDestination.BANK_ACCOUNTS) }
    fun onNavigationHandled() { _uiState.value = SettingsState() }
}
