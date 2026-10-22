package com.example.clarity.domain.model

import com.example.clarity.domain.util.GoalPolicy
import java.util.UUID

enum class GoalStatus {
    ACTIVE,
    COMPLETED,
    ARCHIVED
}

enum class GoalProgressOperationType {
    ADD,
    SET
}

enum class GoalProgressOperationState {
    PENDING,
    APPLIED,
    REJECTED
}

data class FinancialGoal(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val name: String,
    val description: String = "",
    val targetMinor: Long,
    val savedMinor: Long = 0L,
    val currency: String = Currencies.supported.first(),
    val targetDateEpochDay: Long,
    val status: GoalStatus = GoalPolicy.deriveWriteStatus(savedMinor, targetMinor),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val pendingSync: Boolean = false
) {
    val remainingMinor: Long
        get() = GoalPolicy.remainingMinor(targetMinor, savedMinor)

    val progressPercent: Int
        get() = GoalPolicy.progressPercent(savedMinor, targetMinor)

    val progressFraction: Float
        get() = GoalPolicy.progressFraction(savedMinor, targetMinor)

    fun isOverdue(todayEpochDay: Long): Boolean =
        GoalPolicy.isOverdue(this, todayEpochDay)
}

data class GoalProgressOperation(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val goalId: String,
    val type: GoalProgressOperationType,
    val amountMinor: Long,
    val createdAt: Long = 0L,
    val state: GoalProgressOperationState = GoalProgressOperationState.PENDING
)

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
    SAVED_NEGATIVE,
    SAVED_EXCEEDS_TARGET,
    TARGET_BELOW_SAVED,
    INVALID_DATE,
    DATE_NOT_IN_FUTURE,
    CONTRIBUTION_NON_POSITIVE,
    EXCEEDS_TARGET,
    IMMUTABLE_FIELD_CHANGED,
    STATUS_MISMATCH
}

class GoalException(
    val error: GoalError,
    val remainingMinor: Long? = null,
    val currency: String? = null,
    message: String? = null
) : Exception(message ?: error.name)

data class GoalsSnapshot(
    val ownerId: String? = null,
    val goals: List<FinancialGoal> = emptyList(),
    val pendingGoalIds: Set<String> = emptySet(),
    val rejectedOperations: List<GoalProgressOperation> = emptyList()
) {
    fun isGoalSyncing(goal: FinancialGoal): Boolean =
        goal.pendingSync || pendingGoalIds.contains(goal.id)
}

