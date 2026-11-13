package com.example.clarity.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.clarity.MainActivity
import com.example.clarity.R
import com.example.clarity.domain.model.Currencies
import com.example.clarity.domain.repository.SalaryReminderRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.math.BigDecimal

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SalaryReminderWorkerDependencies {
    fun salaryReminders(): SalaryReminderRepository
}

class SalaryReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(
            applicationContext, SalaryReminderWorkerDependencies::class.java
        ).salaryReminders()

        val reminder = repository.get() ?: return Result.success()
        // Schedule next month first so a notification failure never stops the series.
        if (inputData.getBoolean(KEY_ADVANCE, true)) repository.advance()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return Result.success()
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Salary reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )

        val amount = runCatching {
            BigDecimal.valueOf(reminder.amountMinor, Currencies.digits(reminder.currency)).toPlainString()
        }.getOrElse { reminder.amountMinor.toString() }
        val text = "Did you receive your salary of ${reminder.currency} $amount this month? " +
            "Add it to ${reminder.accountName.ifBlank { "your account" }}?"

        val open = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        fun action(name: String, code: Int) = PendingIntent.getBroadcast(
            applicationContext, code,
            Intent(applicationContext, SalaryReminderActionReceiver::class.java).setAction(name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_budget_notification)
            .setContentTitle("Salary received?")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Yes, add it", action(SalaryReminderActionReceiver.ACTION_CONFIRM, 1))
            .addAction(0, "Not yet", action(SalaryReminderActionReceiver.ACTION_SNOOZE, 2))
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    companion object {
        const val KEY_ADVANCE = "advance"
        const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "salary_reminder_channel"
    }
}
