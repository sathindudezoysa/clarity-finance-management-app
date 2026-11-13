package com.example.clarity.data.repository

import android.content.SharedPreferences
import com.example.clarity.data.notifications.SalaryReminderScheduler
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.SalaryReminder
import com.example.clarity.domain.model.SalarySchedule
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.repository.SalaryReminderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SalaryReminderRepositoryImpl @Inject constructor(
    private val prefs: SharedPreferences,
    private val scheduler: SalaryReminderScheduler
) : SalaryReminderRepository {

    override suspend fun get(): SalaryReminder? = withContext(Dispatchers.IO) { read() }

    override suspend fun recordSalary(transaction: Transaction, account: Account) = withContext(Dispatchers.IO) {
        val enteredDate = Instant.ofEpochMilli(transaction.dateEpochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val nextDue = SalarySchedule.firstDue(enteredDate, LocalDate.now())
        val reminder = SalaryReminder(
            accountId = account.id,
            accountName = account.displayLabel,
            amountMinor = transaction.amountMinor,
            currency = transaction.currency,
            salaryDateEpochMillis = transaction.dateEpochMillis,
            dayOfMonth = enteredDate.dayOfMonth,
            nextDueEpochDay = nextDue.toEpochDay(),
            sourceTransactionId = transaction.id,
            sourceUpdatedAt = maxOf(transaction.updatedAt, transaction.createdAt)
        )
        write(reminder)
        scheduler.schedule(reminder.nextDueEpochDay, replace = true)
    }

    override suspend fun advance() = withContext(Dispatchers.IO) {
        val current = read() ?: return@withContext
        val next = SalarySchedule.following(
            LocalDate.ofEpochDay(current.nextDueEpochDay),
            current.dayOfMonth,
            LocalDate.now()
        )
        val updated = current.copy(nextDueEpochDay = next.toEpochDay())
        write(updated)
        scheduler.schedule(updated.nextDueEpochDay, replace = true)
    }

    override suspend fun snooze() = withContext(Dispatchers.IO) {
        scheduler.snooze()
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(KEY_ACCOUNT_ID)
            .remove(KEY_ACCOUNT_NAME)
            .remove(KEY_AMOUNT)
            .remove(KEY_CURRENCY)
            .remove(KEY_SALARY_DATE_MILLIS)
            .remove(KEY_DAY)
            .remove(KEY_DUE)
            .remove(KEY_SOURCE_TX_ID)
            .remove(KEY_SOURCE_UPDATED_AT)
            .apply()
        scheduler.cancel()
    }

    /** Re-arms the scheduled work from SharedPreferences on app startup. */
    fun restore() {
        read()?.let { scheduler.schedule(it.nextDueEpochDay, replace = false) }
    }

    private fun read(): SalaryReminder? {
        val accountId = prefs.getString(KEY_ACCOUNT_ID, null) ?: return null
        val amount = prefs.getLong(KEY_AMOUNT, 0L)
        if (amount <= 0L || !prefs.contains(KEY_DUE)) return null
        return SalaryReminder(
            accountId = accountId,
            accountName = prefs.getString(KEY_ACCOUNT_NAME, "") ?: "",
            amountMinor = amount,
            currency = prefs.getString(KEY_CURRENCY, "LKR") ?: "LKR",
            salaryDateEpochMillis = prefs.getLong(KEY_SALARY_DATE_MILLIS, 0L),
            dayOfMonth = prefs.getInt(KEY_DAY, 1),
            nextDueEpochDay = prefs.getLong(KEY_DUE, 0L),
            sourceTransactionId = prefs.getString(KEY_SOURCE_TX_ID, "") ?: "",
            sourceUpdatedAt = prefs.getLong(KEY_SOURCE_UPDATED_AT, 0L)
        )
    }

    private fun write(reminder: SalaryReminder) {
        prefs.edit()
            .putString(KEY_ACCOUNT_ID, reminder.accountId)
            .putString(KEY_ACCOUNT_NAME, reminder.accountName)
            .putLong(KEY_AMOUNT, reminder.amountMinor)
            .putString(KEY_CURRENCY, reminder.currency)
            .putLong(KEY_SALARY_DATE_MILLIS, reminder.salaryDateEpochMillis)
            .putInt(KEY_DAY, reminder.dayOfMonth)
            .putLong(KEY_DUE, reminder.nextDueEpochDay)
            .putString(KEY_SOURCE_TX_ID, reminder.sourceTransactionId)
            .putLong(KEY_SOURCE_UPDATED_AT, reminder.sourceUpdatedAt)
            .apply()
    }

    private companion object {
        const val PREFIX = "salary_reminder_"
        const val KEY_ACCOUNT_ID = PREFIX + "account_id"
        const val KEY_ACCOUNT_NAME = PREFIX + "account_name"
        const val KEY_AMOUNT = PREFIX + "amount_minor"
        const val KEY_CURRENCY = PREFIX + "currency"
        const val KEY_SALARY_DATE_MILLIS = PREFIX + "date_epoch_millis"
        const val KEY_DAY = PREFIX + "day_of_month"
        const val KEY_DUE = PREFIX + "next_due_epoch_day"
        const val KEY_SOURCE_TX_ID = PREFIX + "source_tx_id"
        const val KEY_SOURCE_UPDATED_AT = PREFIX + "source_updated_at"
    }
}
