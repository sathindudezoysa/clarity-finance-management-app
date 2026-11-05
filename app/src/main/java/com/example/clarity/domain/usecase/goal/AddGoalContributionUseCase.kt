package com.example.clarity.domain.usecase.goal

import com.example.clarity.domain.model.GoalContribution
import com.example.clarity.domain.model.GoalContributionError
import com.example.clarity.domain.model.GoalContributionException
import com.example.clarity.domain.model.GoalError
import com.example.clarity.domain.model.GoalException
import com.example.clarity.domain.repository.GoalContributionRepository
import com.example.clarity.domain.repository.GoalRepository
import com.example.clarity.domain.util.GoalPolicy
import java.util.UUID
import javax.inject.Inject

class AddGoalContributionUseCase @Inject constructor(
    private val goalRepository: GoalRepository,
    private val contributionRepository: GoalContributionRepository
) {
    suspend operator fun invoke(
        goalId: String,
        amountText: String,
        id: String = UUID.randomUUID().toString()
    ): Result<GoalContribution> {
        if (!GoalPolicy.validId(goalId)) {
            return Result.failure(GoalContributionException(GoalContributionError.INVALID_GOAL_ID))
        }
        val goal = goalRepository.getGoal(goalId).getOrElse { error ->
            val mapped = when ((error as? GoalException)?.error) {
                GoalError.SIGN_IN_REQUIRED -> GoalContributionError.SIGN_IN_REQUIRED
                GoalError.INVALID_ID -> GoalContributionError.INVALID_GOAL_ID
                else -> GoalContributionError.GOAL_NOT_FOUND
            }
            return Result.failure(GoalContributionException(mapped))
        } ?: return Result.failure(GoalContributionException(GoalContributionError.GOAL_NOT_FOUND))

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

        return contributionRepository.add(
            GoalContribution(
                id = id,
                userId = goal.userId,
                goalId = goal.id,
                amountMinor = amountMinor,
                currency = goal.currency
            )
        )
    }

    suspend operator fun invoke(contribution: GoalContribution): Result<GoalContribution> =
        contributionRepository.add(contribution)
}

