package com.example.clarity.presentation.goals

import com.example.clarity.domain.model.Currencies

data class GoalEditState(
    val goalId: String? = null,
    val isEditMode: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val fatalError: String? = null,
    val currency: String = Currencies.supported.first(),
    val name: String = "",
    val description: String = "",
    val targetAmount: String = "",
    val targetDateEpochDay: Long = 0L,
    val selectedDateUtcMillis: Long = 0L,
    val targetDateFormatted: String = "",
    val nameError: String? = null,
    val descriptionError: String? = null,
    val targetAmountError: String? = null,
    val targetDateError: String? = null,
    val formError: String? = null
)
