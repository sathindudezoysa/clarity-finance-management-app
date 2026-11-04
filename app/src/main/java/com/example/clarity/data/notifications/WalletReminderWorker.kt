package com.example.clarity.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.clarity.MainActivity
import com.example.clarity.R
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WalletReminderWorkerDependencies {
    fun database(): ClarityDatabase
    fun auth(): AuthRepository
}

class WalletReminderWorker @JvmOverloads constructor(
    context: Context,
    params: WorkerParameters,
    private val injectedDependencies: WalletReminderWorkerDependencies? = null
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deps = injectedDependencies ?: EntryPointAccessors.fromApplication(
            applicationContext,
            WalletReminderWorkerDependencies::class.java
        )

        val user = deps.auth().currentUser.first() ?: return Result.success()
        val db = deps.database()

        val recent = db.transactionDao.getActiveForUser(user.uid)
            .filter { it.accountId != null && it.accountId.startsWith("cash_wallet_") }
            .maxOfOrNull { it.dateEpochMillis } ?: 0L

        val now = System.currentTimeMillis()
        val daysSinceUpdate = if (recent == 0L) 999L else TimeUnit.MILLISECONDS.toDays(now - recent)

        if (daysSinceUpdate < 2L) {
            return Result.success()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val channelId = "wallet_reminder_channel"
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (manager != null) {
            val channel = NotificationChannel(
                channelId,
                "Wallet reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = "android.intent.action.MAIN"
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_budget_notification)
            .setContentTitle("Update your wallet")
            .setContentText("Your cash wallet hasn’t been updated in a while. Add a recent expense, income, or top-up to keep your records current.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Your cash wallet hasn’t been updated in a while. Add a recent expense, income, or top-up to keep your records current."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager?.notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
