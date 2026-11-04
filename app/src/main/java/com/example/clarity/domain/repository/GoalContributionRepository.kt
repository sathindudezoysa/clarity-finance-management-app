package com.example.clarity.domain.repository

import com.example.clarity.domain.model.GoalContribution
import com.example.clarity.domain.model.GoalContributionsSnapshot
import kotlinx.coroutines.flow.Flow

interface GoalContributionRepository {
    fun observeContributions(): Flow<GoalContributionsSnapshot>
    suspend fun add(contribution: GoalContribution): Result<GoalContribution>
    suspend fun retrySync(): Result<Unit>
}

