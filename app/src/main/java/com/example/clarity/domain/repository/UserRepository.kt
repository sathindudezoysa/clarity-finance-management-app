package com.example.clarity.domain.repository

import com.example.clarity.domain.model.User

interface UserRepository {
    suspend fun saveUserProfile(user: User): Result<Unit>
    suspend fun getUserProfile(uid: String): Result<User>
    suspend fun uploadProfilePicture(uid: String, imageUri: String): Result<String>
}
