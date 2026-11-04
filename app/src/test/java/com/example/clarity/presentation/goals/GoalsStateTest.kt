package com.example.clarity.presentation.goals

import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalContributionError
import com.example.clarity.domain.model.GoalContributionException
import com.example.clarity.domain.model.TransactionError
import com.example.clarity.domain.model.TransactionException
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalsStateTest {
    private val futureEpochDay = 20_100L

    private fun sampleGoal(
        id: String = "goal-1",
        userId: String = "user-1",
        name: String = "Emergency Fund",
        description: String = "Six months of expenses",
        targetMinor: Long = 100_000L,
        currency: String = "LKR",
        targetDateEpochDay: Long = futureEpochDay,
        createdAt: Long = 1_000L,
        updatedAt: Long = 1_000L
    ) = FinancialGoal(
        id = id,
        userId = userId,
        name = name,
        description = description,
        targetMinor = targetMinor,
        currency = currency,
        targetDateEpochDay = targetDateEpochDay,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pendingSync = false
    )

    @Test
    fun toCardUiModel_formatsMonthlyRateForEachCurrencyPrecision() {
        val baseDate = LocalDate.of(2026, 1, 10)
        val baseEpoch = baseDate.toEpochDay()
        val threeMonthsEpoch = baseDate.plusMonths(3).toEpochDay()

        val jpyCard = sampleGoal(
            targetMinor = 10_000L,
            currency = "JPY",
            targetDateEpochDay = threeMonthsEpoch
        ).toCardUiModel(baseEpoch)
        assertTrue(jpyCard.isActive)
        assertEquals("JPY 3334 / month", jpyCard.monthlyRateFormatted)

        val kwdCard = sampleGoal(
            targetMinor = 10_000L,
            currency = "KWD",
            targetDateEpochDay = threeMonthsEpoch
        ).toCardUiModel(baseEpoch)
        assertTrue(kwdCard.isActive)
        assertEquals("KWD 3.334 / month", kwdCard.monthlyRateFormatted)
    }

    @Test
    fun toCardUiModel_overdueGoalIsInactiveAndHasNoMonthlyRate() {
        val baseDate = LocalDate.of(2026, 1, 10)
        val baseEpoch = baseDate.toEpochDay()

        val overdueCard = sampleGoal(
            targetMinor = 100_000L,
            currency = "LKR",
            targetDateEpochDay = baseEpoch - 1L
        ).toCardUiModel(baseEpoch)
        assertFalse(overdueCard.isActive)
        assertTrue(overdueCard.isOverdue)
        assertNull(overdueCard.monthlyRateFormatted)
    }

    @Test
    fun calculateMonthlyRateTotals_aggregatesPerCurrencyExcludesOverdueAndHandlesEmptyList() {
        val baseDate = LocalDate.of(2026, 1, 10)
        val baseEpoch = baseDate.toEpochDay()
        val twoMonthsEpoch = baseDate.plusMonths(2).toEpochDay()
        val fourMonthsEpoch = baseDate.plusMonths(4).toEpochDay()

        assertTrue(calculateMonthlyRateTotals(emptyList(), baseEpoch).isEmpty())

        val singleCurrencyGoals = listOf(
            sampleGoal(
                id = "g1",
                targetMinor = 100_000L,
                currency = "LKR",
                targetDateEpochDay = twoMonthsEpoch
            ),
            sampleGoal(
                id = "g2",
                targetMinor = 200_000L,
                currency = "LKR",
                targetDateEpochDay = fourMonthsEpoch
            )
        )
        assertEquals(
            listOf(MonthlyRateTotalUiModel(currency = "LKR", totalFormatted = "LKR 1000.00 / month")),
            calculateMonthlyRateTotals(singleCurrencyGoals, baseEpoch)
        )

        val mixedAndOverdueGoals = listOf(
            sampleGoal(
                id = "usd-1",
                targetMinor = 50_000L,
                currency = "USD",
                targetDateEpochDay = twoMonthsEpoch
            ),
            sampleGoal(
                id = "lkr-1",
                targetMinor = 300_000L,
                currency = "LKR",
                targetDateEpochDay = twoMonthsEpoch
            ),
            sampleGoal(
                id = "jpy-1",
                targetMinor = 10_000L,
                currency = "JPY",
                targetDateEpochDay = fourMonthsEpoch
            ),
            sampleGoal(
                id = "overdue-lkr",
                targetMinor = 900_000L,
                currency = "LKR",
                targetDateEpochDay = baseEpoch - 5L
            )
        )
        assertEquals(
            listOf(
                MonthlyRateTotalUiModel(currency = "LKR", totalFormatted = "LKR 1500.00 / month"),
                MonthlyRateTotalUiModel(currency = "LKR", totalFormatted = "LKR 1500.00 / month").takeIf { false }
                    ?: MonthlyRateTotalUiModel(currency = "USD", totalFormatted = "USD 250.00 / month"),
                MonthlyRateTotalUiModel(currency = "JPY", totalFormatted = "JPY 2500 / month")
            ),
            calculateMonthlyRateTotals(mixedAndOverdueGoals, baseEpoch)
        )
    }

    @Test
    fun goalErrorMessage_mapsFundingAndBalanceErrorsToInlineMessages() {
        assertEquals(
            GoalMessages.CASH_WALLET_REQUIRED,
            goalErrorMessage(
                GoalContributionException(GoalContributionError.CASH_WALLET_REQUIRED),
                GoalMessages.CONTRIBUTION_SAVE_ERROR
            )
        )
        assertEquals(
            GoalMessages.CURRENCY_MISMATCH,
            goalErrorMessage(
                GoalContributionException(GoalContributionError.CURRENCY_MISMATCH),
                GoalMessages.CONTRIBUTION_SAVE_ERROR
            )
        )
        assertEquals(
            GoalMessages.INSUFFICIENT_FUNDS,
            goalErrorMessage(
                GoalContributionException(GoalContributionError.INSUFFICIENT_FUNDS),
                GoalMessages.CONTRIBUTION_SAVE_ERROR
            )
        )
        assertEquals(
            GoalMessages.INSUFFICIENT_FUNDS,
            goalErrorMessage(
                TransactionException(TransactionError.INSUFFICIENT_BALANCE, "Insufficient balance"),
                GoalMessages.CONTRIBUTION_SAVE_ERROR
            )
        )
    }
}

