package com.example.clarity.domain.usecase.auth

import com.example.clarity.domain.model.User
import com.example.clarity.domain.repository.AuthRepository
import javax.inject.Inject

class SignInWithEmailPasswordUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        return repository.signInWithEmailPassword(email, password)
    }
}
