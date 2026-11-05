package com.example.clarity.domain.usecase.goal

import com.example.clarity.domain.model.GoalContribution
import com.example.clarity.domain.model.GoalContributionError
import com.example.clarity.domain.model.GoalContributionException
import com.example.clarity.domain.model.GoalError
import com.example.clarity.domain.model.GoalException
import com.example.clarity.domain.model.TransactionError
import com.example.clarity.domain.model.TransactionException
import com.example.clarity.domain.repository.AccountRepository
import com.example.clarity.domain.repository.GoalFundingRepository
import com.example.clarity.domain.repository.GoalRepository
import com.example.clarity.domain.repository.TransactionRepository
import com.example.clarity.domain.util.GoalFundingPolicy
import com.example.clarity.domain.util.GoalPolicy
import com.example.clarity.domain.util.Resource
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

class ContributeToGoalUseCase @Inject constructor(
    private val goalRepository: GoalRepository,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val goalFundingRepository: GoalFundingRepository
) {
    suspend operator fun invoke(
        goalId: String,
        amountText: String,
        sourceAccountId: String,
        id: String = UUID.randomUUID().toString()
    ): Result<GoalContribution> {
        if (!GoalPolicy.validId(goalId)) {
            return Result.failure(GoalContributionException(GoalContributionError.INVALID_GOAL_ID))
        }
        if (!GoalPolicy.validId(id)) {
            return Result.failure(GoalContributionException(GoalContributionError.INVALID_ID))
        }
        if (sourceAccountId.isBlank() || !GoalPolicy.validId(sourceAccountId)) {
            return Result.failure(GoalContributionException(GoalContributionError.ACCOUNT_REQUIRED))
        }

        val goal = goalRepository.getGoal(goalId).getOrElse { error ->
            val mapped = when ((error as? GoalException)?.error) {
                GoalError.SIGN_IN_REQUIRED -> GoalContributionError.SIGN_IN_REQUIRED
                GoalError.INVALID_ID -> GoalContributionError.INVALID_GOAL_ID
                else -> GoalContributionError.GOAL_NOT_FOUND
            }
            return Result.failure(GoalContributionException(mapped))
        } ?: return Result.failure(GoalContributionException(GoalContributionError.GOAL_NOT_FOUND))

        if (goal.userId.isBlank()) {
            return Result.failure(GoalContributionException(GoalContributionError.SIGN_IN_REQUIRED))
        }

        val amountMinor = GoalPolicy.parseAmountMinor(amountText, goal.currency).getOrElse { error ->
            val mapped = when ((error as? GoalException)?.error) {
                GoalError.INVALID_CURRENCY -> GoalContributionError.INVALID_CURRENCY
                else -> GoalContributionError.INVALID_AMOUNT
            }
            return Result.failure(GoalContributionException(mapped))
        }
        if (amountMinor <= 0L) {
            return Result.failure(GoalContributionException(GoalContributionError.AMOUNT_NON_POSITIVE))
        }

        val accountsResult = accountRepository.observeAccounts()
            .first { it !is Resource.Loading }
        val observedAccounts = accountsResult.data.orEmpty()
        val sourceAccount = accountRepository.getAccount(sourceAccountId).getOrNull()
            ?: observedAccounts.firstOrNull { it.id == sourceAccountId }
            ?: return Result.failure(
                GoalContributionException(GoalContributionError.ACCOUNT_NOT_FOUND)
            )

        if (sourceAccount.isGoalAccount ||
            (sourceAccount.userId.isNotBlank() && sourceAccount.userId != goal.userId)
        ) {
            return Result.failure(
                GoalContributionException(GoalContributionError.ACCOUNT_NOT_FOUND)
            )
        }

        if (!goal.currency.equals(sourceAccount.currency, ignoreCase = true)) {
            return Result.failure(
                GoalContributionException(GoalContributionError.CURRENCY_MISMATCH)
            )
        }

        val allAccounts = if (observedAccounts.any { it.id == sourceAccount.id }) {
            observedAccounts
        } else {
            observedAccounts + sourceAccount
        }

        val txResult = transactionRepository.observeTransactions()
            .first { it !is Resource.Loading }
        val transactions = txResult.data.orEmpty()
        val availability = GoalFundingPolicy.evaluate(
            transactions = transactions,
            accounts = allAccounts,
            currency = goal.currency,
            selectedAccountId = sourceAccount.id
        )
        if (!availability.canAfford(amountMinor)) {
            return Result.failure(
                GoalContributionException(GoalContributionError.INSUFFICIENT_FUNDS)
            )
        }

        return goalFundingRepository.contribute(
            goalId = goal.id,
            amountMinor = amountMinor,
            sourceAccountId = sourceAccount.id,
            id = id
        ).recoverCatching { error ->
            if (error is TransactionException && error.error == TransactionError.INSUFFICIENT_BALANCE) {
                throw GoalContributionException(GoalContributionError.INSUFFICIENT_FUNDS, error.message)
            }
            throw error
        }
    }
}
