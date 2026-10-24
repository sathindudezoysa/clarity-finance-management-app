package com.example.clarity.domain.usecase.goal

import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.repository.GoalRepository
import javax.inject.Inject

class CreateGoalUseCase @Inject constructor(
    private val repository: GoalRepository
) {
    suspend operator fun invoke(goal: FinancialGoal): Result<FinancialGoal> = repository.create(goal)
}

