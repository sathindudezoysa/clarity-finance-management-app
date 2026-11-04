package com.example.clarity.domain.usecase.goal

import com.example.clarity.domain.model.GoalContributionsSnapshot
import com.example.clarity.domain.repository.GoalContributionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveGoalContributionsUseCase @Inject constructor(
    private val repository: GoalContributionRepository
) {
    operator fun invoke(): Flow<GoalContributionsSnapshot> = repository.observeContributions()
}

