package com.example.clarity.domain.usecase.auth

import com.example.clarity.domain.model.User
import com.example.clarity.domain.repository.AuthRepository
import javax.inject.Inject

class SignUpWithEmailPasswordUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        if (email.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Email or password cannot be empty"))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }
        return authRepository.signUpWithEmailPassword(email, password)
    }
}
