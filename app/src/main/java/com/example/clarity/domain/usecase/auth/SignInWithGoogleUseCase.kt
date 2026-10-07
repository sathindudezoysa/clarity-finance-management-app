package com.example.clarity.domain.usecase.auth

import com.example.clarity.domain.model.User
import com.example.clarity.domain.repository.AuthRepository
import javax.inject.Inject

class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(idToken: String): Result<User> {
        if (idToken.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid Google ID Token"))
        }
        return authRepository.signInWithGoogle(idToken)
    }
}
