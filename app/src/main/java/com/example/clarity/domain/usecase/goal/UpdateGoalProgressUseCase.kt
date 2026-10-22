package com.example.clarity.domain.usecase.goal

import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalProgressOperationType
import com.example.clarity.domain.repository.GoalRepository
import javax.inject.Inject

class UpdateGoalProgressUseCase @Inject constructor(
    private val repository: GoalRepository
) {
    suspend operator fun invoke(
        goalId: String,
        operationId: String,
        type: GoalProgressOperationType,
        amountMinor: Long,
        sourceAccountId: String? = null
    ): Result<FinancialGoal> = when (type) {
        GoalProgressOperationType.ADD -> repository.addContribution(
            goalId = goalId,
            operationId = operationId,
            amountMinor = amountMinor,
            sourceAccountId = sourceAccountId.orEmpty()
        )
        GoalProgressOperationType.SET -> repository.setBalance(goalId, operationId, amountMinor)
    }

    suspend fun addContribution(
        goalId: String,
        operationId: String,
        amountMinor: Long,
        sourceAccountId: String
    ): Result<FinancialGoal> = repository.addContribution(
        goalId = goalId,
        operationId = operationId,
        amountMinor = amountMinor,
        sourceAccountId = sourceAccountId
    )

    suspend fun setBalance(
        goalId: String,
        operationId: String,
        amountMinor: Long
    ): Result<FinancialGoal> = repository.setBalance(goalId, operationId, amountMinor)
}
