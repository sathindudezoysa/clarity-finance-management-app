package com.example.clarity.data.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SalaryReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Schedules the monthly reminder for [dueEpochDay] at 09:00 local time. */
    fun schedule(dueEpochDay: Long, replace: Boolean) {
        val due = LocalDate.ofEpochDay(dueEpochDay).atTime(REMINDER_TIME).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val delay = (due - System.currentTimeMillis()).coerceAtLeast(0L)
        enqueue(WORK_MONTHLY, delay, advance = true, replace = replace)
    }

    fun snooze() = enqueue(WORK_SNOOZE, TimeUnit.DAYS.toMillis(1), advance = false, replace = true)

    fun cancel() {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(WORK_MONTHLY)
        manager.cancelUniqueWork(WORK_SNOOZE)
    }

    private fun enqueue(name: String, delayMillis: Long, advance: Boolean, replace: Boolean) {
        val request = OneTimeWorkRequestBuilder<SalaryReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putBoolean(SalaryReminderWorker.KEY_ADVANCE, advance).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            name,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val WORK_MONTHLY = "salary_reminder_monthly"
        const val WORK_SNOOZE = "salary_reminder_snooze"
        private val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)
    }
}
