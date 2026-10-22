package com.example.clarity.presentation.goals

import com.example.clarity.domain.model.GoalProgressOperationType
import java.math.BigInteger

data class GoalAccountOptionUi(
    val id: String,
    val displayLabel: String,
    val currency: String,
    val availableBalanceMinor: BigInteger,
    val availableBalanceFormatted: String,
    val isEligible: Boolean,
    val disabledReason: String? = null
)

data class GoalContributionPreviewUi(
    val accountName: String,
    val accountBeforeFormatted: String,
    val accountAfterFormatted: String,
    val goalBeforeFormatted: String,
    val goalAfterFormatted: String,
    val remainingAfterFormatted: String
)

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
    val accounts: List<GoalAccountOptionUi> = emptyList(),
    val selectedAccountId: String = "",
    val selectedAccountAvailableFormatted: String? = null,
    val hasEligibleAccount: Boolean = false,
    val preview: GoalContributionPreviewUi? = null,
    val isSubmitEnabled: Boolean = false,
    val isSaving: Boolean = false,
    val isSuccess: Boolean = false,
    val accountError: String? = null,
    val amountError: String? = null,
    val error: String? = null
)
