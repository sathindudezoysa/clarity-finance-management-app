package com.example.clarity.domain.util

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/** Transactions are instants displayed as calendar dates in the device's current timezone.
 * Explicit zone arguments keep date picking and budget boundaries deterministic in tests.
 * Defaults are resolved per call so a timezone change takes effect without restarting.
 */
object TransactionCalendar {
    fun dateAt(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun currentMonth(clock: Clock, zone: ZoneId = ZoneId.systemDefault()): String =
        YearMonth.from(dateAt(clock.millis(), zone)).toString()

    fun monthBounds(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> =
        month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() to
            month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    // Material DatePicker encodes a calendar day at UTC midnight, not as a stored transaction instant.
    fun pickerDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        dateAt(epochMillis, zone).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromPickerDate(selectedMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        dateAt(selectedMillis, ZoneOffset.UTC).atStartOfDay(zone).toInstant().toEpochMilli()
}
