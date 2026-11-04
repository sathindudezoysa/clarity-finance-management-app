package com.example.clarity.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.usecase.biometric.ObserveBiometricPreferenceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainAuthViewModel @Inject constructor(
    private val observeBiometricPreferenceUseCase: ObserveBiometricPreferenceUseCase,
    val firebaseAuth: com.google.firebase.auth.FirebaseAuth
) : ViewModel() {

    private val _isBiometricEnabled = MutableStateFlow(false)
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    init {
        viewModelScope.launch {
            observeBiometricPreferenceUseCase().collectLatest { enabled ->
                _isBiometricEnabled.value = enabled
                if (!enabled) {
                    _isUnlocked.value = true
                }
            }
        }
    }

    fun unlockApp() {
        _isUnlocked.value = true
    }

    fun lockApp() {
        _isUnlocked.value = !_isBiometricEnabled.value
    }
}
