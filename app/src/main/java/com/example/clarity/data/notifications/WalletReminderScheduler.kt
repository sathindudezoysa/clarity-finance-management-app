package com.example.clarity.data.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun start() {
        val request = PeriodicWorkRequestBuilder<WalletReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .addTag(TAG_WALLET_REMINDER)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TAG_WALLET_REMINDER,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun triggerNow() {
        val request = OneTimeWorkRequestBuilder<WalletReminderWorker>()
            .setConstraints(Constraints.Builder().build())
            .addTag(TAG_WALLET_REMINDER)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "wallet_reminder_now",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    companion object {
        const val TAG_WALLET_REMINDER = "wallet_reminder_daily"
    }
}
