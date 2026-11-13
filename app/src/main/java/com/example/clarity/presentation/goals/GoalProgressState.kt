package com.example.clarity.presentation.goals

import com.example.clarity.domain.model.ConversionSnapshot
import com.example.clarity.domain.model.Currencies

data class GoalMilestoneUiModel(
    val percent: Int,
    val title: String,
    val requiredFormatted: String,
    val motivationText: String,
    val isReached: Boolean
)

data class GoalFundingAccountUiModel(
    val id: String,
    val label: String,
    val currency: String,
    val balanceFormatted: String,
    val isDefault: Boolean = false
)

data class GoalContributionItemUiModel(
    val id: String,
    val amountFormatted: String,
    val dateFormatted: String,
    val isSyncing: Boolean,
    val sourceAccountLabel: String? = null
)

data class GoalProgressState(
    val goalId: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isFinishing: Boolean = false,
    val fatalError: String? = null,
    val formError: String? = null,
    val name: String = "",
    val description: String = "",
    val currency: String = Currencies.supported.first(),
    val goalAccountLabel: String = "",
    val savedFormatted: String = "",
    val targetFormatted: String = "",
    val targetDateFormatted: String = "",
    val daysRemainingLabel: String = "",
    val isOverdue: Boolean = false,
    val progressFraction: Float = 0f,
    val percentLabel: String = "0% saved",
    val isCompleted: Boolean = false,
    val isClosed: Boolean = false,
    val closedPayoutFormatted: String? = null,
    val closedDestinationAccountLabel: String? = null,
    val closedDateFormatted: String? = null,
    val payoutAccounts: List<GoalFundingAccountUiModel> = emptyList(),
    val selectedPayoutAccountId: String? = null,
    val selectedPayoutAccountLabel: String? = null,
    val selectedPayoutAccountCurrency: String = Currencies.supported.first(),
    val payoutAccountError: String? = null,
    val isCrossCurrencyPayout: Boolean = false,
    val payoutPreview: ConversionSnapshot? = null,
    val payoutPreviewFormatted: String? = null,
    val payoutPreviewLoading: Boolean = false,
    val payoutPreviewError: Boolean = false,
    val fundingAccounts: List<GoalFundingAccountUiModel> = emptyList(),
    val selectedAccountId: String? = null,
    val selectedAccountLabel: String? = null,
    val accountError: String? = null,
    val amountInput: String = "",
    val amountError: String? = null,
    val availableToContributeFormatted: String = "",
    val availabilityLimitLabel: String = "",
    val fundingError: String? = null,
    val isAmountValid: Boolean = false,
    val celebrationTitle: String? = null,
    val celebrationMessage: String? = null,
    val milestones: List<GoalMilestoneUiModel> = emptyList(),
    val contributions: List<GoalContributionItemUiModel> = emptyList()
) {
    val isAddEnabled: Boolean
        get() = !isLoading &&
            !isSaving &&
            !isFinishing &&
            !isCompleted &&
            !isClosed &&
            fatalError == null &&
            fundingError == null &&
            selectedAccountId != null &&
            accountError == null &&
            amountError == null &&
            isAmountValid

    val isFinishEnabled: Boolean
        get() = !isLoading &&
            !isSaving &&
            !isFinishing &&
            isCompleted &&
            !isClosed &&
            fatalError == null &&
            selectedPayoutAccountId != null &&
            payoutAccountError == null &&
            !payoutPreviewLoading &&
            (!isCrossCurrencyPayout || (payoutPreview != null && !payoutPreviewError))
}
