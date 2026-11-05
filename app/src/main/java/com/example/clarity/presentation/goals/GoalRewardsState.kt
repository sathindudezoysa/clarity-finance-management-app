package com.example.clarity.presentation.goals

import com.example.clarity.domain.util.GoalBadge
import com.example.clarity.domain.util.GoalStreakSummary

data class GoalRewardsState(
    val isLoading: Boolean = true,
    val ownerId: String? = null,
    val error: String? = null,
    val streakSummary: GoalStreakSummary = GoalStreakSummary(),
    val badges: List<GoalBadge> = emptyList(),
    val earnedCount: Int = 0
)

