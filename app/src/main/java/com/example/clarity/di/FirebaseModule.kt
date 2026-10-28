package com.example.clarity.di

import android.content.Context
import com.example.clarity.data.local.ClarityDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    private const val SCHEMA_PREFS = "clarity_schema_prefs"
    private const val KEY_SCHEMA_VERSION = "schema_version"

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance().apply {
        maxUploadRetryTimeMillis = 15_000L
        maxDownloadRetryTimeMillis = 15_000L
        maxOperationRetryTimeMillis = 15_000L
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope
    ): FirebaseFirestore {
        applicationScope.launch(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(SCHEMA_PREFS, Context.MODE_PRIVATE)
            if (prefs.getInt(KEY_SCHEMA_VERSION, -1) != ClarityDatabase.SCHEMA_VERSION) {
                context.databaseList()
                    .filter { it.startsWith("firestore.") }
                    .forEach { context.deleteDatabase(it) }
                prefs.edit().putInt(KEY_SCHEMA_VERSION, ClarityDatabase.SCHEMA_VERSION).apply()
            }
        }
        return FirebaseFirestore.getInstance().apply {
            try {
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            } catch (_: IllegalStateException) {
                // Firestore was already started earlier in the process lifecycle.
            }
        }
    }
}
