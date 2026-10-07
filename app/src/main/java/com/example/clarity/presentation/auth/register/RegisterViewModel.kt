package com.example.clarity.presentation.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.usecase.auth.SignInWithGoogleUseCase
import com.example.clarity.domain.usecase.auth.SignUpWithEmailPasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val signUpWithEmailPasswordUseCase: SignUpWithEmailPasswordUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterState())
    val state: StateFlow<RegisterState> = _state.asStateFlow()

    fun onRegisterClick(email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = signUpWithEmailPasswordUseCase(email, password)
            result.onSuccess {
                _state.update { it.copy(isLoading = false, isSuccess = true) }
            }.onFailure { exception ->
                _state.update { it.copy(isLoading = false, error = exception.message ?: "An unexpected error occurred") }
            }
        }
    }

    fun onGoogleSignIn(idToken: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = signInWithGoogleUseCase(idToken)
            result.onSuccess {
                _state.update { it.copy(isLoading = false, isSuccess = true) }
            }.onFailure { exception ->
                _state.update { it.copy(isLoading = false, error = exception.message ?: "Google Sign-In failed") }
            }
        }
    }

    fun setErrorMessage(message: String) {
        _state.update { it.copy(isLoading = false, error = message) }
    }
}
