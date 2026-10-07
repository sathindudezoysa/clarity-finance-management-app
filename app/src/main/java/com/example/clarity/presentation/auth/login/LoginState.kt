package com.example.clarity.presentation.auth.login

data class LoginState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val isPasswordResetDialogVisible: Boolean = false,
    val passwordResetMessage: String? = null
)
