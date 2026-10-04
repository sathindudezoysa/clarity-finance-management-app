package com.example.clarity.domain.repository

import com.example.clarity.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<User?>
    
    suspend fun signInWithEmailPassword(email: String, password: String): Result<User>
    suspend fun signUpWithEmailPassword(email: String, password: String): Result<User>
    suspend fun signInWithGoogle(idToken: String): Result<User>
    suspend fun signOut()
}
