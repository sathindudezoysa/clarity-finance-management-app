package com.example.clarity.presentation.settings

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.data.repository.AuthRepositoryImpl
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.usecase.auth.ObserveCurrentUserUseCase
import com.example.clarity.domain.usecase.auth.SignOutUseCase
import com.example.clarity.domain.usecase.auth.UpdateUserProfileUseCase
import com.example.clarity.domain.usecase.currency.ObserveCurrencyPreferenceUseCase
import com.example.clarity.domain.usecase.currency.SetCurrencyPreferenceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SharedPreferences,
    private val observeCurrencyPreference: ObserveCurrencyPreferenceUseCase,
    private val setCurrencyPreference: SetCurrencyPreferenceUseCase,
    private val observeCurrentUser: ObserveCurrentUserUseCase,
    private val updateUserProfile: UpdateUserProfileUseCase,
    private val getAccounts: GetAccountsUseCase,
    private val signOutUseCase: SignOutUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(loadCachedState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeCurrencyPreference().collect { preference ->
                prefs.edit().putString(KEY_CURRENCY, preference.currency).apply()
                _uiState.update {
                    it.copy(
                        currency = preference.currency,
                        pendingSync = preference.pendingSync
                    )
                }
            }
        }
        viewModelScope.launch {
            observeCurrentUser()
                .catch { emit(null) }
                .collect { user ->
                    if (user != null) {
                        val resolvedName = user.displayName?.takeIf { it.isNotBlank() }
                            ?: prefs.getString(AuthRepositoryImpl.KEY_USER_DISPLAY_NAME, null)?.takeIf { it.isNotBlank() }
                            ?: user.email?.substringBefore("@")
                        prefs.edit()
                            .putString(AuthRepositoryImpl.KEY_USER_UID, user.uid)
                            .putString(AuthRepositoryImpl.KEY_USER_EMAIL, user.email)
                            .putString(AuthRepositoryImpl.KEY_USER_DISPLAY_NAME, resolvedName)
                            .putBoolean(AuthRepositoryImpl.KEY_USER_VERIFIED, user.isEmailVerified)
                            .apply()
                        _uiState.update {
                            it.copy(
                                uid = user.uid,
                                displayName = resolvedName,
                                email = user.email,
                                isVerified = user.isEmailVerified
                            )
                        }
                    }
                }
        }
        viewModelScope.launch {
            getAccounts()
                .catch { }
                .collect { result ->
                    val accounts = result.data.orEmpty()
                    val bankAccounts = accounts.filterNot { it.isCashWallet }
                    val defaultAccount = bankAccounts.firstOrNull { it.isDefault } ?: bankAccounts.firstOrNull()
                    val accountLabel = defaultAccount?.displayLabel
                    prefs.edit().putString(KEY_DEFAULT_ACCOUNT, accountLabel).apply()
                    _uiState.update {
                        it.copy(defaultAccountName = accountLabel)
                    }
                }
        }
    }

    fun setCurrency(currency: String) {
        if (_uiState.value.saving) return
        _uiState.update { it.copy(saving = true, error = false) }
        viewModelScope.launch {
            val result = setCurrencyPreference(currency)
            if (result.isSuccess) {
                prefs.edit().putString(KEY_CURRENCY, currency).apply()
            }
            _uiState.update { it.copy(saving = false, error = result.isFailure) }
        }
    }

    fun onDisplayNameChange(name: String) {
        _uiState.update {
            it.copy(
                displayName = name,
                profileSaved = false,
                profileError = null
            )
        }
    }

    fun toggleBiometric(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
        _uiState.update { it.copy(biometricEnabled = enabled) }
    }

    fun saveProfileChanges() {
        val currentName = _uiState.value.displayName?.trim().orEmpty()
        if (_uiState.value.isUpdatingProfile) return
        _uiState.update { it.copy(isUpdatingProfile = true, profileError = null, profileSaved = false) }
        viewModelScope.launch {
            if (currentName.isNotBlank()) {
                prefs.edit().putString(AuthRepositoryImpl.KEY_USER_DISPLAY_NAME, currentName).apply()
                val result = updateUserProfile(currentName)
                if (result.isFailure) {
                    _uiState.update {
                        it.copy(
                            isUpdatingProfile = false,
                            profileError = result.exceptionOrNull()?.message
                        )
                    }
                    return@launch
                }
            }
            _uiState.update {
                it.copy(
                    isUpdatingProfile = false,
                    profileSaved = true
                )
            }
            delay(2000)
            _uiState.update { it.copy(profileSaved = false) }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            runCatching { signOutUseCase() }
            _uiState.update { it.copy(destination = SettingsDestination.LOGIN) }
        }
    }

    fun onBack() {
        _uiState.update { it.copy(destination = SettingsDestination.BACK) }
    }

    fun onBankAccounts() {
        _uiState.update { it.copy(destination = SettingsDestination.BANK_ACCOUNTS) }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(destination = null) }
    }

    private fun loadCachedState(): SettingsState {
        return SettingsState(
            uid = prefs.getString(AuthRepositoryImpl.KEY_USER_UID, null),
            displayName = prefs.getString(AuthRepositoryImpl.KEY_USER_DISPLAY_NAME, null),
            email = prefs.getString(AuthRepositoryImpl.KEY_USER_EMAIL, null),
            isVerified = prefs.getBoolean(AuthRepositoryImpl.KEY_USER_VERIFIED, false),
            tierLabel = prefs.getString(KEY_TIER_LABEL, null),
            streakLabel = prefs.getString(KEY_STREAK_LABEL, null),
            currency = prefs.getString(KEY_CURRENCY, "LKR") ?: "LKR",
            defaultAccountName = prefs.getString(KEY_DEFAULT_ACCOUNT, null),
            cashThreshold = prefs.getString(KEY_CASH_THRESHOLD, null),
            cloudSyncSchedule = prefs.getString(KEY_CLOUD_SYNC_SCHEDULE, null),
            biometricEnabled = if (prefs.contains(KEY_BIOMETRIC_ENABLED)) {
                prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
            } else null,
            twoFactorStatus = prefs.getString(KEY_TWO_FACTOR_STATUS, null),
            appearanceMode = prefs.getString(KEY_APPEARANCE_MODE, null)
        )
    }

    companion object {
        const val KEY_CURRENCY = "settings_currency"
        const val KEY_DEFAULT_ACCOUNT = "settings_default_account"
        const val KEY_BIOMETRIC_ENABLED = "settings_biometric_enabled"
        const val KEY_TIER_LABEL = "settings_tier_label"
        const val KEY_STREAK_LABEL = "settings_streak_label"
        const val KEY_CASH_THRESHOLD = "settings_cash_threshold"
        const val KEY_CLOUD_SYNC_SCHEDULE = "settings_cloud_sync_schedule"
        const val KEY_TWO_FACTOR_STATUS = "settings_two_factor_status"
        const val KEY_APPEARANCE_MODE = "settings_appearance_mode"
    }
}
