package com.example.clarity.domain.repository

import com.example.clarity.domain.model.GoalContribution

interface GoalFundingRepository {
    suspend fun contribute(
        goalId: String,
        amountMinor: Long,
        sourceAccountId: String,
        id: String
    ): Result<GoalContribution>
}
