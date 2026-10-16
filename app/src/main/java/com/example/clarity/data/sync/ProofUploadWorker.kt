package com.example.clarity.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.clarity.domain.repository.AuthRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Default WorkManager construction, with dependencies resolved from the application's Hilt graph. */
class ProofUploadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString(OWNER_UID) ?: return Result.failure()
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val auth = dependencies.authRepository()
        if (auth.currentUser.first()?.uid != uid) return Result.success()
        return try {
            val complete = dependencies.proofSyncProcessor().syncPending(uid)
            if (complete || auth.currentUser.first()?.uid != uid) Result.success() else Result.retry()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (auth.currentUser.first()?.uid != uid) Result.success() else Result.retry()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun proofSyncProcessor(): ProofSyncProcessor
        fun authRepository(): AuthRepository
    }

    companion object { const val OWNER_UID = "proof_owner_uid" }
}
