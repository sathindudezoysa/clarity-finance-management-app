package com.example.clarity.domain.util

import com.example.clarity.domain.model.Currencies
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalContribution
import com.example.clarity.domain.model.GoalContributionError
import com.example.clarity.domain.model.GoalContributionException
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

enum class GoalMilestoneThreshold(
    val percent: Int,
    val title: String,
    val motivationText: String
) {
    QUARTER(
        percent = 25,
        title = "25% Milestone",
        motivationText = "Great start! You have reached 25% of your goal."
    ),
    HALF(
        percent = 50,
        title = "50% Milestone",
        motivationText = "Halfway there! You have reached 50% of your goal."
    ),
    THREE_QUARTERS(
        percent = 75,
        title = "75% Milestone",
        motivationText = "Almost there! You have reached 75% of your goal."
    ),
    COMPLETE(
        percent = 100,
        title = "Goal Reached!",
        motivationText = "Congratulations! You have reached 100% of your financial goal."
    )
}

data class GoalMilestoneStatus(
    val threshold: GoalMilestoneThreshold,
    val requiredMinor: Long,
    val isReached: Boolean
)

data class GoalProgressSummary(
    val goalId: String,
    val targetMinor: Long,
    val savedMinor: BigInteger,
    val savedCappedToLong: Long,
    val progressFraction: Float,
    val displayPercent: Int,
    val isCompleted: Boolean,
    val milestones: List<GoalMilestoneStatus>,
    val reachedMilestones: List<GoalMilestoneThreshold>,
    val nextMilestone: GoalMilestoneStatus?
)

data class GoalStreakSummary(
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val isAtRisk: Boolean = false,
    val contributedToday: Boolean = false
)

enum class GoalBadgeType {
    FIRST_STEP,
    GOAL_25,
    GOAL_50,
    GOAL_75,
    GOAL_REACHED,
    STREAK_3,
    STREAK_7,
    STREAK_30
}

data class GoalBadge(
    val id: String,
    val type: GoalBadgeType,
    val title: String,
    val description: String,
    val goalId: String? = null,
    val goalName: String? = null,
    val isEarned: Boolean
)

object GoalProgressPolicy {
    const val MOTIVATION_START = "Every contribution counts. Take your first step today!"
    const val MOTIVATION_25 = "Great start! You have reached 25% of your goal."
    const val MOTIVATION_50 = "Halfway there! You have reached 50% of your goal."
    const val MOTIVATION_75 = "Almost there! You have reached 75% of your goal."
    const val MOTIVATION_100 = "Congratulations! You have reached 100% of your financial goal."
    const val STREAK_AT_RISK_HINT = "No contribution logged today yet — add progress today to keep your streak alive!"

    fun sumSavedMinor(contributions: Iterable<GoalContribution>): BigInteger {
        var sum = BigInteger.ZERO
        for (c in contributions) {
            if (c.amountMinor > 0L) {
                sum = sum.add(BigInteger.valueOf(c.amountMinor))
            }
        }
        return sum
    }

    fun sumSavedMinorExactOrMax(contributions: Iterable<GoalContribution>): Long {
        var sum = 0L
        for (c in contributions) {
            if (c.amountMinor > 0L) {
                sum = try {
                    Math.addExact(sum, c.amountMinor)
                } catch (_: ArithmeticException) {
                    Long.MAX_VALUE
                }
            }
        }
        return sum
    }

    fun requiredMinorForMilestone(targetMinor: Long, percent: Int): Long {
        if (targetMinor <= 0L) return 0L
        return BigDecimal.valueOf(targetMinor)
            .multiply(BigDecimal.valueOf(percent.toLong()))
            .divide(BigDecimal.valueOf(100L), 0, RoundingMode.CEILING)
            .longValueExact()
    }

    fun evaluateGoalProgress(
        goal: FinancialGoal,
        contributions: List<GoalContribution>
    ): GoalProgressSummary {
        val goalContributions = contributions.filter {
            it.goalId == goal.id && it.amountMinor > 0L && it.currency == goal.currency
        }
        return evaluateGoalProgress(
            goalId = goal.id,
            targetMinor = goal.targetMinor,
            savedMinor = sumSavedMinor(goalContributions)
        )
    }

    fun evaluateGoalProgress(
        goalId: String,
        targetMinor: Long,
        savedMinor: BigInteger
    ): GoalProgressSummary {
        val safeSaved = if (savedMinor < BigInteger.ZERO) BigInteger.ZERO else savedMinor
        val savedCapped = if (safeSaved > BigInteger.valueOf(Long.MAX_VALUE)) {
            Long.MAX_VALUE
        } else {
            safeSaved.toLong()
        }

        if (targetMinor <= 0L) {
            return GoalProgressSummary(
                goalId = goalId,
                targetMinor = targetMinor,
                savedMinor = safeSaved,
                savedCappedToLong = savedCapped,
                progressFraction = 0f,
                displayPercent = 0,
                isCompleted = false,
                milestones = emptyList(),
                reachedMilestones = emptyList(),
                nextMilestone = null
            )
        }

        val targetBigInt = BigInteger.valueOf(targetMinor)
        val completed = safeSaved >= targetBigInt
        val targetDec = BigDecimal.valueOf(targetMinor)
        val savedDec = BigDecimal(safeSaved)

        val fraction = if (completed) {
            1f
        } else {
            savedDec.divide(targetDec, 4, RoundingMode.DOWN)
                .toFloat()
                .coerceIn(0f, 1f)
        }

        val rawPercent = savedDec
            .multiply(BigDecimal.valueOf(100L))
            .divide(targetDec, 0, RoundingMode.DOWN)
            .min(BigDecimal.valueOf(100L))
            .toInt()
            .coerceIn(0, 100)
        val displayPercent = if (completed) 100 else rawPercent.coerceAtMost(99)

        val milestones = GoalMilestoneThreshold.values().map { threshold ->
            val req = requiredMinorForMilestone(targetMinor, threshold.percent)
            val reached = safeSaved >= BigInteger.valueOf(req)
            GoalMilestoneStatus(
                threshold = threshold,
                requiredMinor = req,
                isReached = reached
            )
        }
        val reached = milestones.filter { it.isReached }.map { it.threshold }
        val next = milestones.firstOrNull { !it.isReached }

        return GoalProgressSummary(
            goalId = goalId,
            targetMinor = targetMinor,
            savedMinor = safeSaved,
            savedCappedToLong = savedCapped,
            progressFraction = fraction,
            displayPercent = displayPercent,
            isCompleted = completed,
            milestones = milestones,
            reachedMilestones = reached,
            nextMilestone = next
        )
    }

    fun newlyCrossedMilestone(
        targetMinor: Long,
        previousSavedMinor: BigInteger,
        newSavedMinor: BigInteger
    ): GoalMilestoneThreshold? {
        if (targetMinor <= 0L || newSavedMinor <= previousSavedMinor) return null
        val before = evaluateGoalProgress("", targetMinor, previousSavedMinor).reachedMilestones.toSet()
        val after = evaluateGoalProgress("", targetMinor, newSavedMinor).reachedMilestones
        return after.filter { it !in before }.maxByOrNull { it.percent }
    }

    fun calculateStreak(
        contributions: List<GoalContribution>,
        todayEpochDay: Long
    ): GoalStreakSummary {
        val validDays = contributions
            .asSequence()
            .filter { it.amountMinor > 0L && GoalPolicy.validEpochDay(it.dateEpochDay) && it.dateEpochDay <= todayEpochDay }
            .map { it.dateEpochDay }
            .toSortedSet()

        if (validDays.isEmpty()) {
            return GoalStreakSummary(
                currentStreakDays = 0,
                longestStreakDays = 0,
                isAtRisk = false,
                contributedToday = false
            )
        }

        var longest = 0
        var run = 0
        var prevDay: Long? = null
        for (day in validDays) {
            if (prevDay != null && day == prevDay + 1L) {
                run += 1
            } else {
                run = 1
            }
            if (run > longest) {
                longest = run
            }
            prevDay = day
        }

        val hasToday = todayEpochDay in validDays
        val yesterday = todayEpochDay - 1L
        val hasYesterday = yesterday in validDays

        val anchorDay: Long? = when {
            hasToday -> todayEpochDay
            hasYesterday -> yesterday
            else -> null
        }

        var current = 0
        if (anchorDay != null) {
            var cursor = anchorDay
            while (cursor in validDays) {
                current += 1
                cursor -= 1L
            }
        }

        val atRisk = !hasToday && hasYesterday && current > 0
        return GoalStreakSummary(
            currentStreakDays = current,
            longestStreakDays = maxOf(longest, current),
            isAtRisk = atRisk,
            contributedToday = hasToday
        )
    }

    fun deriveBadges(
        goals: List<FinancialGoal>,
        contributions: List<GoalContribution>,
        todayEpochDay: Long
    ): List<GoalBadge> {
        val validContributions = contributions.filter { it.amountMinor > 0L }
        val streak = calculateStreak(validContributions, todayEpochDay)
        val badges = mutableListOf<GoalBadge>()

        badges.add(
            GoalBadge(
                id = "badge_first_step",
                type = GoalBadgeType.FIRST_STEP,
                title = "First Step",
                description = "Make your first goal contribution",
                isEarned = validContributions.isNotEmpty()
            )
        )

        badges.add(
            GoalBadge(
                id = "badge_streak_3",
                type = GoalBadgeType.STREAK_3,
                title = "3-Day Streak",
                description = "Contribute to your goals 3 days in a row",
                isEarned = streak.longestStreakDays >= 3
            )
        )
        badges.add(
            GoalBadge(
                id = "badge_streak_7",
                type = GoalBadgeType.STREAK_7,
                title = "7-Day Streak",
                description = "Contribute to your goals 7 days in a row",
                isEarned = streak.longestStreakDays >= 7
            )
        )
        badges.add(
            GoalBadge(
                id = "badge_streak_30",
                type = GoalBadgeType.STREAK_30,
                title = "30-Day Streak",
                description = "Contribute to your goals 30 days in a row",
                isEarned = streak.longestStreakDays >= 30
            )
        )

        for (goal in goals) {
            val summary = evaluateGoalProgress(goal, validContributions)
            val reachedSet = summary.reachedMilestones.toSet()
            badges.add(
                GoalBadge(
                    id = "badge_goal_${goal.id}_25",
                    type = GoalBadgeType.GOAL_25,
                    title = "${goal.name}: 25%",
                    description = "Save 25% of ${goal.name}",
                    goalId = goal.id,
                    goalName = goal.name,
                    isEarned = GoalMilestoneThreshold.QUARTER in reachedSet
                )
            )
            badges.add(
                GoalBadge(
                    id = "badge_goal_${goal.id}_50",
                    type = GoalBadgeType.GOAL_50,
                    title = "${goal.name}: 50%",
                    description = "Save 50% of ${goal.name}",
                    goalId = goal.id,
                    goalName = goal.name,
                    isEarned = GoalMilestoneThreshold.HALF in reachedSet
                )
            )
            badges.add(
                GoalBadge(
                    id = "badge_goal_${goal.id}_75",
                    type = GoalBadgeType.GOAL_75,
                    title = "${goal.name}: 75%",
                    description = "Save 75% of ${goal.name}",
                    goalId = goal.id,
                    goalName = goal.name,
                    isEarned = GoalMilestoneThreshold.THREE_QUARTERS in reachedSet
                )
            )
            badges.add(
                GoalBadge(
                    id = "badge_goal_${goal.id}_100",
                    type = GoalBadgeType.GOAL_REACHED,
                    title = "${goal.name}: Goal Reached",
                    description = "Reach 100% of ${goal.name}",
                    goalId = goal.id,
                    goalName = goal.name,
                    isEarned = summary.isCompleted
                )
            )
        }

        return badges
    }

    fun validateForCreate(
        contribution: GoalContribution,
        todayEpochDay: Long
    ): GoalContribution {
        if (!GoalPolicy.validId(contribution.id) ||
            (contribution.userId.isNotEmpty() && !GoalPolicy.validId(contribution.userId))
        ) {
            throw GoalContributionException(GoalContributionError.INVALID_ID)
        }
        if (!GoalPolicy.validId(contribution.goalId)) {
            throw GoalContributionException(GoalContributionError.INVALID_GOAL_ID)
        }
        if (contribution.currency !in Currencies.supported) {
            throw GoalContributionException(GoalContributionError.INVALID_CURRENCY)
        }
        if (contribution.amountMinor <= 0L) {
            throw GoalContributionException(GoalContributionError.AMOUNT_NON_POSITIVE)
        }
        if (!GoalPolicy.validEpochDay(todayEpochDay)) {
            throw GoalContributionException(GoalContributionError.INVALID_DATE)
        }
        return contribution.copy(dateEpochDay = todayEpochDay)
    }

    fun validateStoredOrRemote(contribution: GoalContribution) {
        if (!GoalPolicy.validId(contribution.id) ||
            (contribution.userId.isNotEmpty() && !GoalPolicy.validId(contribution.userId))
        ) {
            throw GoalContributionException(GoalContributionError.INVALID_ID)
        }
        if (!GoalPolicy.validId(contribution.goalId)) {
            throw GoalContributionException(GoalContributionError.INVALID_GOAL_ID)
        }
        if (contribution.currency !in Currencies.supported) {
            throw GoalContributionException(GoalContributionError.INVALID_CURRENCY)
        }
        if (contribution.amountMinor <= 0L) {
            throw GoalContributionException(GoalContributionError.AMOUNT_NON_POSITIVE)
        }
        if (!GoalPolicy.validEpochDay(contribution.dateEpochDay)) {
            throw GoalContributionException(GoalContributionError.INVALID_DATE)
        }
        if (contribution.createdAt < 0L || contribution.updatedAt < contribution.createdAt) {
            throw GoalContributionException(GoalContributionError.INVALID_DATE)
        }
    }
}

