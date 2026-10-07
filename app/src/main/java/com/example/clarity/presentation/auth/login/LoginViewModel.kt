package com.example.clarity.presentation.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.usecase.auth.SendPasswordResetEmailUseCase
import com.example.clarity.domain.usecase.auth.SignInWithEmailPasswordUseCase
import com.example.clarity.domain.usecase.auth.SignInWithGoogleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val signInWithEmailPasswordUseCase: SignInWithEmailPasswordUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val sendPasswordResetEmailUseCase: SendPasswordResetEmailUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun onLoginClick(email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = signInWithEmailPasswordUseCase(email, password)
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

    fun showPasswordResetDialog() {
        _state.update { it.copy(isPasswordResetDialogVisible = true, passwordResetMessage = null, error = null) }
    }

    fun hidePasswordResetDialog() {
        _state.update { it.copy(isPasswordResetDialogVisible = false) }
    }

    fun onSendPasswordReset(email: String) {
        if (email.isBlank()) {
            _state.update { it.copy(error = "Please enter your email to reset password.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, passwordResetMessage = null) }
            val result = sendPasswordResetEmailUseCase(email)
            result.onSuccess {
                _state.update { 
                    it.copy(
                        isLoading = false, 
                        isPasswordResetDialogVisible = false,
                        passwordResetMessage = "Password reset email sent. Please check your inbox."
                    ) 
                }
            }.onFailure { exception ->
                _state.update { 
                    it.copy(
                        isLoading = false, 
                        error = exception.message ?: "Failed to send reset email."
                    ) 
                }
            }
        }
    }

    fun setErrorMessage(message: String) {
        _state.update { it.copy(isLoading = false, error = message) }
    }
}
