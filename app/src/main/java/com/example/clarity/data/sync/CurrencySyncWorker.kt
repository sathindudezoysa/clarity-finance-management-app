package com.example.clarity.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.clarity.data.repository.CurrencyPreferenceRepositoryImpl
import com.example.clarity.data.repository.TransactionRepositoryImpl
import com.example.clarity.domain.repository.AuthRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CurrencyWorkerDependencies {
    fun transactions(): TransactionRepositoryImpl
    fun preferences(): CurrencyPreferenceRepositoryImpl
    fun auth(): AuthRepository
}

class CurrencySyncWorker @JvmOverloads constructor(
    context: Context, params: WorkerParameters,
    private val injectedDependencies: CurrencyWorkerDependencies? = null
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dependencies = injectedDependencies ?: EntryPointAccessors.fromApplication(applicationContext, CurrencyWorkerDependencies::class.java)
        val uid = inputData.getString("uid") ?: return Result.failure()
        if (dependencies.auth().currentUser.first()?.uid != uid) return Result.success()
        return try {
            // A Firestore outage must not prevent a reachable rate provider resolving local entries.
            suspend fun attempt(block: suspend () -> Boolean): Boolean = try { block() } catch (error: Exception) {
                if (error is CancellationException) throw error
                false
            }
            val preferencesSynced = attempt { withTimeoutOrNull(20_000) { dependencies.preferences().sync(uid) } ?: false }
            val transactionsSynced = attempt { withTimeoutOrNull(120_000) { dependencies.transactions().backgroundSync(uid) } ?: false }
            if (preferencesSynced && transactionsSynced) Result.success() else Result.retry()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.retry()
        }
    }
}
