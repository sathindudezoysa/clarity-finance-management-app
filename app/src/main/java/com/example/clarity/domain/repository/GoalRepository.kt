package com.example.clarity.domain.repository

import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalsSnapshot
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    fun observeGoals(): Flow<GoalsSnapshot>
    suspend fun getGoal(id: String): Result<FinancialGoal?>
    suspend fun create(goal: FinancialGoal): Result<FinancialGoal>
    suspend fun edit(goal: FinancialGoal): Result<FinancialGoal>
    suspend fun addContribution(goalId: String, operationId: String, amountMinor: Long): Result<FinancialGoal>
    suspend fun setBalance(goalId: String, operationId: String, amountMinor: Long): Result<FinancialGoal>
    suspend fun dismissRejectedOperation(operationId: String): Result<Unit>
    suspend fun retrySync(): Result<Unit>
}

