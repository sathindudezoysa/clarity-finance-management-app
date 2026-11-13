package com.example.clarity.domain.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * Most recent user-entered salary details persisted in SharedPreferences.
 * [nextDueEpochDay] is the calendar day when the next monthly salary notification triggers.
 */
data class SalaryReminder(
    val accountId: String,
    val accountName: String,
    val amountMinor: Long,
    val currency: String,
    val salaryDateEpochMillis: Long,
    val dayOfMonth: Int,
    val nextDueEpochDay: Long,
    val sourceTransactionId: String = "",
    val sourceUpdatedAt: Long = 0L
)

object SalarySchedule {
    /** Schedules the reminder for the same day in the month following [enteredDate] (and strictly after [today]). */
    fun firstDue(enteredDate: LocalDate, today: LocalDate = LocalDate.now()): LocalDate =
        following(enteredDate, enteredDate.dayOfMonth, today)

    /** Next monthly occurrence strictly after [after] and strictly after [today]. */
    fun following(after: LocalDate, dayOfMonth: Int, today: LocalDate = LocalDate.now()): LocalDate {
        var next = sameDayInMonth(YearMonth.from(after).plusMonths(1), dayOfMonth)
        while (!next.isAfter(today)) {
            next = sameDayInMonth(YearMonth.from(next).plusMonths(1), dayOfMonth)
        }
        return next
    }

    private fun sameDayInMonth(month: YearMonth, day: Int): LocalDate =
        month.atDay(minOf(day, month.lengthOfMonth()))
}
