package com.example.clarity.data.repository

import android.content.SharedPreferences
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
    private val firebaseAuth: FirebaseAuth,
    private val prefs: SharedPreferences
) : AuthRepository {

    override val currentUser: Flow<User?> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser?.toUser()
            if (user != null) {
                cacheUser(user)
            }
            trySend(user)
        }
        firebaseAuth.addAuthStateListener(authStateListener)
        
        awaitClose {
            firebaseAuth.removeAuthStateListener(authStateListener)
        }
    }

    override suspend fun signInWithEmailPassword(email: String, password: String): Result<User> {
        return try {
            val user = authSource.signInWithEmail(email, password)?.toUser()
            if (user != null) {
                cacheUser(user)
                Result.success(user)
            } else {
                Result.failure(Exception("Login failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signUpWithEmailPassword(email: String, password: String): Result<User> {
        return try {
            val user = authSource.signUpWithEmail(email, password)?.toUser()
            if (user != null) {
                cacheUser(user)
                Result.success(user)
            } else {
                Result.failure(Exception("Sign up failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<User> {
        return try {
            val user = authSource.signInWithGoogle(idToken)?.toUser()
            if (user != null) {
                cacheUser(user)
                Result.success(user)
            } else {
                Result.failure(Exception("Google Sign in failed"))
            }
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

    override suspend fun updateProfile(displayName: String): Result<User> {
        return try {
            val trimmed = displayName.trim()
            val updatedFirebaseUser = authSource.updateDisplayName(trimmed)
            val current = updatedFirebaseUser?.toUser()?.copy(displayName = trimmed)
                ?: prefs.getString(KEY_USER_UID, null)?.let { uid ->
                    User(
                        uid = uid,
                        email = prefs.getString(KEY_USER_EMAIL, null),
                        displayName = trimmed,
                        isEmailVerified = prefs.getBoolean(KEY_USER_VERIFIED, false)
                    )
                }
            if (current != null) {
                cacheUser(current)
                Result.success(current)
            } else {
                Result.failure(Exception("No signed-in user"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        authSource.signOut()
        prefs.edit()
            .remove(KEY_USER_UID)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_DISPLAY_NAME)
            .remove(KEY_USER_VERIFIED)
            .apply()
    }

    private fun cacheUser(user: User) {
        prefs.edit()
            .putString(KEY_USER_UID, user.uid)
            .putString(KEY_USER_EMAIL, user.email)
            .putString(KEY_USER_DISPLAY_NAME, user.displayName)
            .putBoolean(KEY_USER_VERIFIED, user.isEmailVerified)
            .apply()
    }

    private fun FirebaseUser.toUser(): User {
        val cachedName = if (prefs.getString(KEY_USER_UID, null) == this.uid) {
            prefs.getString(KEY_USER_DISPLAY_NAME, null)
        } else null
        return User(
            uid = this.uid,
            email = this.email,
            displayName = this.displayName?.takeIf { it.isNotBlank() } ?: cachedName,
            isEmailVerified = this.isEmailVerified
        )
    }

    companion object {
        const val KEY_USER_UID = "user_uid"
        const val KEY_USER_EMAIL = "user_email"
        const val KEY_USER_DISPLAY_NAME = "user_display_name"
        const val KEY_USER_VERIFIED = "user_is_verified"
    }
}
