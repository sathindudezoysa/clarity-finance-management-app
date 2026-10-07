package com.example.clarity.data.repository

import android.net.Uri
import com.example.clarity.domain.model.User
import com.example.clarity.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth
) : UserRepository {
    
    override suspend fun saveUserProfile(user: User): Result<Unit> {
        return try {
            // Also map it to a hashmap or dataclass directly. Since User is a data class,
            // we can just set it. Firestore SDK can serialize data classes directly.
            firestore.collection("users").document(user.uid).set(user).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserProfile(uid: String): Result<User> {
        return try {
            val snapshot = firestore.collection("users").document(uid).get().await()
            if (snapshot.exists()) {
                val email = snapshot.getString("email")
                val displayName = snapshot.getString("displayName")
                val photoUrl = snapshot.getString("photoUrl")
                val preferredCurrency = snapshot.getString("preferredCurrency")
                val isSetupComplete = snapshot.getBoolean("isSetupComplete") ?: false
                
                val user = User(
                    uid = uid,
                    email = email,
                    displayName = displayName,
                    photoUrl = photoUrl,
                    preferredCurrency = preferredCurrency,
                    isSetupComplete = isSetupComplete
                )
                Result.success(user)
            } else {
                val currentUser = auth.currentUser
                if (currentUser != null && currentUser.uid == uid) {
                    val user = User(
                        uid = uid,
                        email = currentUser.email,
                        displayName = currentUser.displayName,
                        photoUrl = null,
                        preferredCurrency = null,
                        isSetupComplete = false
                    )
                    Result.success(user)
                } else {
                    Result.failure(Exception("User profile not found"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadProfilePicture(uid: String, imageUri: String): Result<String> {
        return try {
            val ref = storage.reference.child("profile_images/$uid.jpg")
            ref.putFile(Uri.parse(imageUri)).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
