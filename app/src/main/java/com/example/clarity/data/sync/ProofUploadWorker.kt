package com.example.clarity.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.clarity.domain.repository.AuthRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Default WorkManager construction, with dependencies resolved from the application's Hilt graph. */
class ProofUploadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString(OWNER_UID) ?: return Result.failure()
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        return when (proofUploadDecision(uid, dependencies.authRepository().currentUser) {
            dependencies.proofSyncProcessor().syncPending(uid)
        }) {
            ProofUploadDecision.COMPLETE -> Result.success()
            ProofUploadDecision.RETRY -> Result.retry()
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
