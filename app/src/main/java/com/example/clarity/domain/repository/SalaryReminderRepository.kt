package com.example.clarity.domain.repository

import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.SalaryReminder
import com.example.clarity.domain.model.Transaction

interface SalaryReminderRepository {
    suspend fun get(): SalaryReminder?
    /** Saves the most recent salary transaction to SharedPreferences and schedules next month's reminder. */
    suspend fun recordSalary(transaction: Transaction, account: Account)
    /** Moves the reminder to the following month's occurrence and schedules it. */
    suspend fun advance()
    /** Schedules a re-prompt for tomorrow without changing the monthly salary day. */
    suspend fun snooze()
    suspend fun clear()
}
