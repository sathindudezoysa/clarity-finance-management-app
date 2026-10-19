package com.example.clarity.data.sync

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyWorkScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun schedule(uid: String, replace: Boolean = false) {
        val manager = WorkManager.getInstance(context)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val input = workDataOf("uid" to uid)
        manager.enqueueUniqueWork("currency-now-$uid", if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CurrencySyncWorker>().setInputData(input).setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build())
        // Also closes the enqueue-versus-worker-completion race and covers wallet outbox writes.
        manager.enqueueUniquePeriodicWork("currency-periodic-$uid", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CurrencySyncWorker>(30, TimeUnit.MINUTES)
                .setInputData(input).setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build())
    }
}
