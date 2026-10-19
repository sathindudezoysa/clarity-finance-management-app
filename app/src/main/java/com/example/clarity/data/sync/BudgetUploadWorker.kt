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

class BudgetUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString("owner") ?: return Result.failure()
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        return try {
            if (deps.auth().currentUser.first()?.uid != uid) Result.success()
            else if (deps.budgetProcessor().upload(uid)) Result.success() else Result.retry()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.retry()
        }
    }
    @EntryPoint @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun auth(): AuthRepository
        fun budgetProcessor(): BudgetSyncProcessor
    }
}
