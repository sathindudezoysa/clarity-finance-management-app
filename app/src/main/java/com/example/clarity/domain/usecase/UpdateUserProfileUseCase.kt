package com.example.clarity.domain.usecase

import com.example.clarity.domain.model.User
import com.example.clarity.domain.repository.UserRepository
import javax.inject.Inject

class UpdateUserProfileUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(
        user: User, 
        newImageUri: String? = null
    ): Result<Unit> {
        var updatedUser = user
        
        if (newImageUri != null) {
            val uploadResult = userRepository.uploadProfilePicture(user.uid, newImageUri)
            uploadResult.onSuccess { downloadUrl ->
                updatedUser = updatedUser.copy(photoUrl = downloadUrl)
            }.onFailure { exception ->
                return Result.failure(exception)
            }
        }
        
        return userRepository.saveUserProfile(updatedUser)
    }
}
