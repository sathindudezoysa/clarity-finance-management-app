package com.example.clarity.domain.model

import com.example.clarity.domain.util.GoalPolicy
import java.util.UUID

data class FinancialGoal(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val name: String,
    val description: String = "",
    val targetMinor: Long,
    val currency: String = Currencies.supported.first(),
    val targetDateEpochDay: Long,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val pendingSync: Boolean = false
) {
    fun daysRemaining(todayEpochDay: Long): Long =
        GoalPolicy.daysRemaining(targetDateEpochDay, todayEpochDay)

    fun isOverdue(todayEpochDay: Long): Boolean =
        GoalPolicy.isOverdue(targetDateEpochDay, todayEpochDay)

    fun isActive(todayEpochDay: Long): Boolean =
        GoalPolicy.isActive(targetDateEpochDay, todayEpochDay)

    fun remainingMonths(todayEpochDay: Long): Long =
        GoalPolicy.remainingMonths(targetDateEpochDay, todayEpochDay)

    fun requiredMonthlySavingsMinor(todayEpochDay: Long): Long? =
        GoalPolicy.requiredMonthlySavingsMinor(targetMinor, targetDateEpochDay, todayEpochDay)
}

enum class GoalError {
    SIGN_IN_REQUIRED,
    NOT_FOUND,
    INVALID_ID,
    NAME_BLANK,
    NAME_TOO_LONG,
    DESCRIPTION_TOO_LONG,
    INVALID_CURRENCY,
    INVALID_AMOUNT,
    TARGET_NON_POSITIVE,
    INVALID_DATE,
    DATE_NOT_IN_FUTURE,
    IMMUTABLE_FIELD_CHANGED
}

class GoalException(
    val error: GoalError,
    message: String? = null
) : Exception(message ?: error.name)

data class GoalsSnapshot(
    val ownerId: String? = null,
    val goals: List<FinancialGoal> = emptyList()
)
