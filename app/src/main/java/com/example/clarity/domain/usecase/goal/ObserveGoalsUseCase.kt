package com.example.clarity.domain.usecase.goal

import com.example.clarity.domain.model.GoalsSnapshot
import com.example.clarity.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveGoalsUseCase @Inject constructor(
    private val repository: GoalRepository
) {
    operator fun invoke(): Flow<GoalsSnapshot> = repository.observeGoals()
}

