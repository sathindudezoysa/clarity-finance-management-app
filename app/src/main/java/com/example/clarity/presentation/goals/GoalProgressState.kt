package com.example.clarity.presentation.goals

import com.example.clarity.domain.model.GoalProgressOperationType

data class GoalProgressState(
    val isLoading: Boolean = true,
    val canSave: Boolean = false,
    val goalId: String = "",
    val goalName: String = "",
    val currency: String = "LKR",
    val targetFormatted: String = "",
    val currentSavedFormatted: String = "",
    val remainingFormatted: String = "",
    val progressPercent: Int = 0,
    val progressFraction: Float = 0f,
    val mode: GoalProgressOperationType = GoalProgressOperationType.ADD,
    val amount: String = "",
    val isSaving: Boolean = false,
    val isSuccess: Boolean = false,
    val amountError: String? = null,
    val error: String? = null
)

