package com.example.clarity.domain.util

import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalContribution
import com.example.clarity.domain.model.GoalContributionError
import com.example.clarity.domain.model.GoalContributionException
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GoalProgressPolicyTest {
    private val todayEpochDay = 20_500L

    private fun sampleGoal(
        id: String = "goal-1",
        userId: String = "user-1",
        name: String = "Emergency Fund",
        targetMinor: Long = 100_000L,
        currency: String = "LKR",
        targetDateEpochDay: Long = todayEpochDay + 90L
    ) = FinancialGoal(
        id = id,
        userId = userId,
        name = name,
        description = "Test goal",
        targetMinor = targetMinor,
        currency = currency,
        targetDateEpochDay = targetDateEpochDay,
        createdAt = 1_000L,
        updatedAt = 1_000L
    )

    private fun contribution(
        id: String = "c-1",
        goalId: String = "goal-1",
        amountMinor: Long = 10_000L,
        currency: String = "LKR",
        dateEpochDay: Long = todayEpochDay
    ) = GoalContribution(
        id = id,
        userId = "user-1",
        goalId = goalId,
        amountMinor = amountMinor,
        currency = currency,
        dateEpochDay = dateEpochDay,
        createdAt = 1_000L,
        updatedAt = 1_000L
    )

    @Test
    fun evaluateGoalProgress_computesFractionMilestonesAndCapsDisplayAt100Percent() {
        val goal = sampleGoal(targetMinor = 100_000L)

        val zeroSummary = GoalProgressPolicy.evaluateGoalProgress(goal, emptyList())
        assertEquals(BigInteger.ZERO, zeroSummary.savedMinor)
        assertEquals(0f, zeroSummary.progressFraction, 0.0001f)
        assertEquals(0, zeroSummary.displayPercent)
        assertFalse(zeroSummary.isCompleted)
        assertTrue(zeroSummary.reachedMilestones.isEmpty())
        assertEquals(GoalMilestoneThreshold.QUARTER, zeroSummary.nextMilestone?.threshold)

        val halfSummary = GoalProgressPolicy.evaluateGoalProgress(
            goal,
            listOf(
                contribution(id = "c1", amountMinor = 25_000L),
                contribution(id = "c2", amountMinor = 25_000L)
            )
        )
        assertEquals(BigInteger.valueOf(50_000L), halfSummary.savedMinor)
        assertEquals(0.5f, halfSummary.progressFraction, 0.0001f)
        assertEquals(50, halfSummary.displayPercent)
        assertFalse(halfSummary.isCompleted)
        assertEquals(
            listOf(GoalMilestoneThreshold.QUARTER, GoalMilestoneThreshold.HALF),
            halfSummary.reachedMilestones
        )
        assertEquals(GoalMilestoneThreshold.THREE_QUARTERS, halfSummary.nextMilestone?.threshold)

        val exceededSummary = GoalProgressPolicy.evaluateGoalProgress(
            goal,
            listOf(
                contribution(id = "c1", amountMinor = 75_000L),
                contribution(id = "c2", amountMinor = 50_000L)
            )
        )
        assertEquals(BigInteger.valueOf(125_000L), exceededSummary.savedMinor)
        assertEquals(1f, exceededSummary.progressFraction, 0.0001f)
        assertEquals(100, exceededSummary.displayPercent)
        assertTrue(exceededSummary.isCompleted)
        assertEquals(GoalMilestoneThreshold.values().toList(), exceededSummary.reachedMilestones)
        assertNull(exceededSummary.nextMilestone)
    }

    @Test
    fun evaluateGoalProgress_handlesLongOverflowWithoutFailing() {
        val goal = sampleGoal(targetMinor = Long.MAX_VALUE)
        val contributions = listOf(
            contribution(id = "c1", amountMinor = Long.MAX_VALUE - 10L),
            contribution(id = "c2", amountMinor = 100L)
        )
        val summary = GoalProgressPolicy.evaluateGoalProgress(goal, contributions)
        val expectedSaved = BigInteger.valueOf(Long.MAX_VALUE - 10L).add(BigInteger.valueOf(100L))
        assertEquals(expectedSaved, summary.savedMinor)
        assertEquals(Long.MAX_VALUE, summary.savedCappedToLong)
        assertEquals(1f, summary.progressFraction, 0.0001f)
        assertEquals(100, summary.displayPercent)
        assertTrue(summary.isCompleted)

        val exactOrMax = GoalProgressPolicy.sumSavedMinorExactOrMax(contributions)
        assertEquals(Long.MAX_VALUE, exactOrMax)
    }

    @Test
    fun newlyCrossedMilestone_detectsHighestCrossedThreshold() {
        val target = 100_000L
        assertNull(
            GoalProgressPolicy.newlyCrossedMilestone(
                targetMinor = target,
                previousSavedMinor = BigInteger.valueOf(10_000L),
                newSavedMinor = BigInteger.valueOf(20_000L)
            )
        )
        assertEquals(
            GoalMilestoneThreshold.QUARTER,
            GoalProgressPolicy.newlyCrossedMilestone(
                targetMinor = target,
                previousSavedMinor = BigInteger.valueOf(20_000L),
                newSavedMinor = BigInteger.valueOf(25_000L)
            )
        )
        assertEquals(
            GoalMilestoneThreshold.THREE_QUARTERS,
            GoalProgressPolicy.newlyCrossedMilestone(
                targetMinor = target,
                previousSavedMinor = BigInteger.valueOf(20_000L),
                newSavedMinor = BigInteger.valueOf(80_000L)
            )
        )
        assertEquals(
            GoalMilestoneThreshold.COMPLETE,
            GoalProgressPolicy.newlyCrossedMilestone(
                targetMinor = target,
                previousSavedMinor = BigInteger.valueOf(80_000L),
                newSavedMinor = BigInteger.valueOf(120_000L)
            )
        )
    }

    @Test
    fun calculateStreak_handlesTodayYesterdayAtRiskAndGaps() {
        val emptyStreak = GoalProgressPolicy.calculateStreak(emptyList(), todayEpochDay)
        assertEquals(0, emptyStreak.currentStreakDays)
        assertEquals(0, emptyStreak.longestStreakDays)
        assertFalse(emptyStreak.isAtRisk)
        assertFalse(emptyStreak.contributedToday)

        val activeTodayStreak = GoalProgressPolicy.calculateStreak(
            listOf(
                contribution(id = "c1", dateEpochDay = todayEpochDay - 2L),
                contribution(id = "c2", dateEpochDay = todayEpochDay - 1L),
                contribution(id = "c3", dateEpochDay = todayEpochDay),
                contribution(id = "c4", dateEpochDay = todayEpochDay)
            ),
            todayEpochDay
        )
        assertEquals(3, activeTodayStreak.currentStreakDays)
        assertEquals(3, activeTodayStreak.longestStreakDays)
        assertFalse(activeTodayStreak.isAtRisk)
        assertTrue(activeTodayStreak.contributedToday)

        val atRiskStreak = GoalProgressPolicy.calculateStreak(
            listOf(
                contribution(id = "c1", dateEpochDay = todayEpochDay - 5L),
                contribution(id = "c2", dateEpochDay = todayEpochDay - 4L),
                contribution(id = "c3", dateEpochDay = todayEpochDay - 3L),
                contribution(id = "c4", dateEpochDay = todayEpochDay - 1L)
            ),
            todayEpochDay
        )
        assertEquals(1, atRiskStreak.currentStreakDays)
        assertEquals(3, atRiskStreak.longestStreakDays)
        assertTrue(atRiskStreak.isAtRisk)
        assertFalse(atRiskStreak.contributedToday)

        val brokenStreak = GoalProgressPolicy.calculateStreak(
            listOf(
                contribution(id = "c1", dateEpochDay = todayEpochDay - 4L),
                contribution(id = "c2", dateEpochDay = todayEpochDay - 3L),
                contribution(id = "c3", dateEpochDay = todayEpochDay - 2L)
            ),
            todayEpochDay
        )
        assertEquals(0, brokenStreak.currentStreakDays)
        assertEquals(3, brokenStreak.longestStreakDays)
        assertFalse(brokenStreak.isAtRisk)
        assertFalse(brokenStreak.contributedToday)
    }

    @Test
    fun deriveBadges_unlocksFirstStepMilestoneAndStreakBadges() {
        val goal = sampleGoal(id = "g1", name = "Emergency Fund", targetMinor = 100_000L)
        val initialBadges = GoalProgressPolicy.deriveBadges(listOf(goal), emptyList(), todayEpochDay)
        assertTrue(initialBadges.none { it.isEarned })

        val contributions = (0L..6L).map { offset ->
            contribution(
                id = "c-$offset",
                goalId = "g1",
                amountMinor = 10_000L,
                dateEpochDay = todayEpochDay - offset
            )
        }
        val badges = GoalProgressPolicy.deriveBadges(listOf(goal), contributions, todayEpochDay)
        val byType = badges.associateBy { it.type }

        assertNotNull(byType[GoalBadgeType.FIRST_STEP])
        assertTrue(byType.getValue(GoalBadgeType.FIRST_STEP).isEarned)
        assertTrue(byType.getValue(GoalBadgeType.STREAK_3).isEarned)
        assertTrue(byType.getValue(GoalBadgeType.STREAK_7).isEarned)
        assertFalse(byType.getValue(GoalBadgeType.STREAK_30).isEarned)

        assertTrue(byType.getValue(GoalBadgeType.GOAL_25).isEarned)
        assertTrue(byType.getValue(GoalBadgeType.GOAL_50).isEarned)
        assertFalse(byType.getValue(GoalBadgeType.GOAL_75).isEarned)
        assertFalse(byType.getValue(GoalBadgeType.GOAL_REACHED).isEarned)
    }

    @Test
    fun validateForCreate_enforcesPositiveAmountSupportedCurrencyAndValidIds() {
        val valid = GoalProgressPolicy.validateForCreate(
            contribution(amountMinor = 5_000L, dateEpochDay = 0L),
            todayEpochDay
        )
        assertEquals(todayEpochDay, valid.dateEpochDay)

        try {
            GoalProgressPolicy.validateForCreate(contribution(amountMinor = 0L), todayEpochDay)
            fail("Expected AMOUNT_NON_POSITIVE")
        } catch (e: GoalContributionException) {
            assertEquals(GoalContributionError.AMOUNT_NON_POSITIVE, e.error)
        }

        try {
            GoalProgressPolicy.validateForCreate(contribution(currency = "XYZ"), todayEpochDay)
            fail("Expected INVALID_CURRENCY")
        } catch (e: GoalContributionException) {
            assertEquals(GoalContributionError.INVALID_CURRENCY, e.error)
        }

        try {
            GoalProgressPolicy.validateForCreate(contribution(goalId = "bad/goal"), todayEpochDay)
            fail("Expected INVALID_GOAL_ID")
        } catch (e: GoalContributionException) {
            assertEquals(GoalContributionError.INVALID_GOAL_ID, e.error)
        }
    }
}

