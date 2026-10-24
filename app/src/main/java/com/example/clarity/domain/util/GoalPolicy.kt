package com.example.clarity.domain.util

import com.example.clarity.domain.model.Currencies
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalError
import com.example.clarity.domain.model.GoalException
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

object GoalPolicy {
    const val MAX_NAME_LENGTH = 100
    const val MAX_DESCRIPTION_LENGTH = 500
    private const val MIN_EPOCH_DAY = -365243219162L
    private const val MAX_EPOCH_DAY = 365241780471L

    fun validId(id: String): Boolean =
        id.isNotBlank() &&
            id.length <= 128 &&
            id != "." &&
            id != ".." &&
            id.none { it == '/' || it == '\\' || it.isISOControl() }

    fun validEpochDay(epochDay: Long): Boolean =
        epochDay in MIN_EPOCH_DAY..MAX_EPOCH_DAY &&
            runCatching { LocalDate.ofEpochDay(epochDay) }.isSuccess

    fun todayEpochDay(clock: Clock, zone: ZoneId = ZoneId.systemDefault()): Long =
        TransactionCalendar.dateAt(clock.millis(), zone).toEpochDay()

    fun daysRemaining(targetDateEpochDay: Long, todayEpochDay: Long): Long =
        Math.subtractExact(targetDateEpochDay, todayEpochDay)

    fun isOverdue(targetDateEpochDay: Long, todayEpochDay: Long): Boolean =
        targetDateEpochDay < todayEpochDay

    fun parseAmountMinor(text: String, currency: String): Result<Long> = runCatching {
        if (currency !in Currencies.supported) {
            throw GoalException(GoalError.INVALID_CURRENCY)
        }
        val digits = Currencies.digits(currency)
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 40) {
            throw GoalException(GoalError.INVALID_AMOUNT)
        }
        if (trimmed.any { it == '+' || it == '-' }) {
            throw GoalException(GoalError.INVALID_AMOUNT)
        }
        val pattern = if (digits == 0) {
            Regex("[0-9]+")
        } else {
            Regex("[0-9]+(?:\\.[0-9]{1,$digits})?")
        }
        if (!trimmed.matches(pattern)) {
            throw GoalException(GoalError.INVALID_AMOUNT)
        }
        val parsed = try {
            BigDecimal(trimmed).movePointRight(digits).longValueExact()
        } catch (_: ArithmeticException) {
            throw GoalException(GoalError.INVALID_AMOUNT)
        } catch (_: NumberFormatException) {
            throw GoalException(GoalError.INVALID_AMOUNT)
        }
        MoneyUtils.parseMinor(trimmed, currency).getOrElse {
            throw GoalException(GoalError.INVALID_AMOUNT)
        }.also {
            if (it != parsed) throw GoalException(GoalError.INVALID_AMOUNT)
        }
    }

    fun validateForCreate(goal: FinancialGoal, todayEpochDay: Long): FinancialGoal {
        val normalized = normalizeAndValidateCommon(goal)
        if (normalized.targetDateEpochDay <= todayEpochDay) {
            throw GoalException(GoalError.DATE_NOT_IN_FUTURE)
        }
        return normalized
    }

    fun validateForEdit(
        existing: FinancialGoal,
        updated: FinancialGoal,
        todayEpochDay: Long
    ): FinancialGoal {
        if (existing.id != updated.id ||
            (updated.userId.isNotEmpty() && existing.userId != updated.userId) ||
            existing.currency != updated.currency ||
            existing.createdAt != updated.createdAt
        ) {
            throw GoalException(GoalError.IMMUTABLE_FIELD_CHANGED)
        }
        val merged = updated.copy(
            id = existing.id,
            userId = existing.userId,
            currency = existing.currency,
            createdAt = existing.createdAt
        )
        val normalized = normalizeAndValidateCommon(merged)
        if (normalized.targetDateEpochDay != existing.targetDateEpochDay &&
            normalized.targetDateEpochDay <= todayEpochDay
        ) {
            throw GoalException(GoalError.DATE_NOT_IN_FUTURE)
        }
        return normalized
    }

    fun validateStoredOrRemote(goal: FinancialGoal) {
        if (!validId(goal.id) || (goal.userId.isNotEmpty() && !validId(goal.userId))) {
            throw GoalException(GoalError.INVALID_ID)
        }
        val trimmedName = goal.name.trim()
        if (trimmedName.isEmpty()) throw GoalException(GoalError.NAME_BLANK)
        if (goal.name.length > MAX_NAME_LENGTH || goal.name.any { it.isISOControl() }) {
            throw GoalException(GoalError.NAME_TOO_LONG)
        }
        if (goal.description.length > MAX_DESCRIPTION_LENGTH) {
            throw GoalException(GoalError.DESCRIPTION_TOO_LONG)
        }
        if (goal.currency !in Currencies.supported) {
            throw GoalException(GoalError.INVALID_CURRENCY)
        }
        if (goal.targetMinor <= 0L) throw GoalException(GoalError.TARGET_NON_POSITIVE)
        if (!validEpochDay(goal.targetDateEpochDay)) throw GoalException(GoalError.INVALID_DATE)
        if (goal.createdAt < 0L || goal.updatedAt < goal.createdAt) {
            throw GoalException(GoalError.INVALID_DATE)
        }
    }

    private fun normalizeAndValidateCommon(goal: FinancialGoal): FinancialGoal {
        if (!validId(goal.id) || (goal.userId.isNotEmpty() && !validId(goal.userId))) {
            throw GoalException(GoalError.INVALID_ID)
        }
        val trimmedName = goal.name.trim()
        if (trimmedName.isEmpty()) {
            throw GoalException(GoalError.NAME_BLANK)
        }
        if (trimmedName.length > MAX_NAME_LENGTH || trimmedName.any { it.isISOControl() }) {
            throw GoalException(GoalError.NAME_TOO_LONG)
        }
        val trimmedDescription = goal.description.trim()
        if (trimmedDescription.length > MAX_DESCRIPTION_LENGTH) {
            throw GoalException(GoalError.DESCRIPTION_TOO_LONG)
        }
        if (goal.currency !in Currencies.supported) {
            throw GoalException(GoalError.INVALID_CURRENCY)
        }
        if (goal.targetMinor <= 0L) {
            throw GoalException(GoalError.TARGET_NON_POSITIVE)
        }
        if (!validEpochDay(goal.targetDateEpochDay)) {
            throw GoalException(GoalError.INVALID_DATE)
        }
        return goal.copy(
            name = trimmedName,
            description = trimmedDescription
        )
    }
}
