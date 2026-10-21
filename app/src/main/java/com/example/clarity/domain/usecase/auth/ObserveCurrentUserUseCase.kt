package com.example.clarity.domain.usecase.auth

import com.example.clarity.domain.model.User
import com.example.clarity.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveCurrentUserUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    operator fun invoke(): Flow<User?> = repository.currentUser
}
