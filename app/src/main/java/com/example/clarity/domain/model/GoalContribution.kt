package com.example.clarity.domain.model

import java.util.UUID

data class GoalContribution(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val goalId: String,
    val amountMinor: Long,
    val currency: String = Currencies.supported.first(),
    val dateEpochDay: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val pendingSync: Boolean = false
)

enum class GoalContributionError {
    SIGN_IN_REQUIRED,
    GOAL_NOT_FOUND,
    INVALID_ID,
    INVALID_GOAL_ID,
    INVALID_CURRENCY,
    INVALID_AMOUNT,
    AMOUNT_NON_POSITIVE,
    INVALID_DATE
}

class GoalContributionException(
    val error: GoalContributionError,
    message: String? = null
) : Exception(message ?: error.name)

data class GoalContributionsSnapshot(
    val ownerId: String? = null,
    val contributions: List<GoalContribution> = emptyList()
)

