package com.example.clarity.domain.usecase

import com.example.clarity.domain.model.User
import com.example.clarity.domain.repository.UserRepository
import javax.inject.Inject

class GetUserProfileUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(uid: String): Result<User> {
        return userRepository.getUserProfile(uid)
    }
}
