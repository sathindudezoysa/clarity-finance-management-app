package com.example.clarity.presentation.goals

data class GoalEditState(
    val isEditMode: Boolean = false,
    val isLoading: Boolean = true,
    val canSave: Boolean = false,
    val name: String = "",
    val targetAmount: String = "",
    val savedAmount: String = "0",
    val description: String = "",
    val currency: String = "LKR",
    val targetDateEpochDay: Long = 0L,
    val selectedDateUtcMillis: Long = 0L,
    val formattedTargetDate: String = "",
    val currentSavedFormatted: String = "",
    val isSaving: Boolean = false,
    val isSuccess: Boolean = false,
    val nameError: String? = null,
    val targetAmountError: String? = null,
    val savedAmountError: String? = null,
    val targetDateError: String? = null,
    val descriptionError: String? = null,
    val error: String? = null
)

