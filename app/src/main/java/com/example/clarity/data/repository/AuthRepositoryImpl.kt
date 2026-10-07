package com.example.clarity.data.repository

import com.example.clarity.data.remote.FirebaseAuthSource
import com.example.clarity.domain.model.User
import com.example.clarity.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val authSource: FirebaseAuthSource,
    private val firebaseAuth: FirebaseAuth
) : AuthRepository {

    override val currentUser: Flow<User?> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toUser())
        }
        firebaseAuth.addAuthStateListener(authStateListener)
        
        awaitClose {
            firebaseAuth.removeAuthStateListener(authStateListener)
        }
    }

    override suspend fun signInWithEmailPassword(email: String, password: String): Result<User> {
        return try {
            val user = authSource.signInWithEmail(email, password)
            if (user != null) Result.success(user.toUser()) else Result.failure(Exception("Login failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signUpWithEmailPassword(email: String, password: String): Result<User> {
        return try {
            val user = authSource.signUpWithEmail(email, password)
            if (user != null) Result.success(user.toUser()) else Result.failure(Exception("Sign up failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<User> {
        return try {
            val user = authSource.signInWithGoogle(idToken)
            if (user != null) Result.success(user.toUser()) else Result.failure(Exception("Google Sign in failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            authSource.sendPasswordResetEmail(email)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        authSource.signOut()
    }

    private fun FirebaseUser.toUser(): User {
        return User(
            uid = this.uid,
            email = this.email,
            displayName = this.displayName
        )
    }
}
