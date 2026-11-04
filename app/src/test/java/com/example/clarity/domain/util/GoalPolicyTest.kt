package com.example.clarity.domain.util

import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalError
import com.example.clarity.domain.model.GoalException
import com.example.clarity.presentation.goals.MonthlyRateTotalUiModel
import com.example.clarity.presentation.goals.calculateMonthlyRateTotals
import com.example.clarity.presentation.goals.toCardUiModel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GoalPolicyTest {
    private val todayEpochDay = 20_000L
    private val futureEpochDay = 20_100L
    private val pastEpochDay = 19_900L

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

    private fun assertGoalError(expected: GoalError, block: () -> Unit) {
        try {
            block()
            fail("Expected GoalException($expected)")
        } catch (e: GoalException) {
            assertEquals(expected, e.error)
        }
    }

    @Test
    fun validateForCreate_acceptsValidGoalAndTrimsText() {
        val validated = GoalPolicy.validateForCreate(
            sampleGoal(
                name = "  New Laptop  ",
                description = " Work machine ",
                targetMinor = 250_000L
            ),
            todayEpochDay
        )
        assertEquals("New Laptop", validated.name)
        assertEquals("Work machine", validated.description)
        assertEquals(250_000L, validated.targetMinor)
    }

    @Test
    fun validateForCreate_rejectsInvalidNameAndDescription() {
        assertGoalError(GoalError.NAME_BLANK) {
            GoalPolicy.validateForCreate(sampleGoal(name = "   "), todayEpochDay)
        }
        assertGoalError(GoalError.NAME_TOO_LONG) {
            GoalPolicy.validateForCreate(sampleGoal(name = "a".repeat(101)), todayEpochDay)
        }
        assertGoalError(GoalError.DESCRIPTION_TOO_LONG) {
            GoalPolicy.validateForCreate(sampleGoal(description = "d".repeat(501)), todayEpochDay)
        }
    }

    @Test
    fun validateForCreate_rejectsInvalidTargetCurrencyAndDates() {
        assertGoalError(GoalError.TARGET_NON_POSITIVE) {
            GoalPolicy.validateForCreate(sampleGoal(targetMinor = 0L), todayEpochDay)
        }
        assertGoalError(GoalError.TARGET_NON_POSITIVE) {
            GoalPolicy.validateForCreate(sampleGoal(targetMinor = -500L), todayEpochDay)
        }
        assertGoalError(GoalError.INVALID_CURRENCY) {
            GoalPolicy.validateForCreate(sampleGoal(currency = "ZZZ"), todayEpochDay)
        }
        assertGoalError(GoalError.DATE_NOT_IN_FUTURE) {
            GoalPolicy.validateForCreate(sampleGoal(targetDateEpochDay = todayEpochDay), todayEpochDay)
        }
        assertGoalError(GoalError.DATE_NOT_IN_FUTURE) {
            GoalPolicy.validateForCreate(sampleGoal(targetDateEpochDay = pastEpochDay), todayEpochDay)
        }
        assertGoalError(GoalError.INVALID_DATE) {
            GoalPolicy.validateForCreate(sampleGoal(targetDateEpochDay = Long.MAX_VALUE), todayEpochDay)
        }
    }

    @Test
    fun validateForEdit_allowsUnchangedPastDeadlineAndRejectsImmutableFieldOrNewPastDeadline() {
        val existingWithPastDeadline = sampleGoal(
            targetMinor = 100_000L,
            targetDateEpochDay = pastEpochDay
        )
        val edited = GoalPolicy.validateForEdit(
            existing = existingWithPastDeadline,
            updated = existingWithPastDeadline.copy(
                name = "Updated Name",
                description = "Updated description",
                targetMinor = 80_000L,
                targetDateEpochDay = pastEpochDay
            ),
            todayEpochDay = todayEpochDay
        )
        assertEquals("Updated Name", edited.name)
        assertEquals("Updated description", edited.description)
        assertEquals(80_000L, edited.targetMinor)

        assertGoalError(GoalError.DATE_NOT_IN_FUTURE) {
            GoalPolicy.validateForEdit(
                existing = existingWithPastDeadline,
                updated = existingWithPastDeadline.copy(targetDateEpochDay = todayEpochDay),
                todayEpochDay = todayEpochDay
            )
        }

        assertGoalError(GoalError.IMMUTABLE_FIELD_CHANGED) {
            GoalPolicy.validateForEdit(
                existing = existingWithPastDeadline,
                updated = existingWithPastDeadline.copy(currency = "USD"),
                todayEpochDay = todayEpochDay
            )
        }

        assertGoalError(GoalError.IMMUTABLE_FIELD_CHANGED) {
            GoalPolicy.validateForEdit(
                existing = existingWithPastDeadline,
                updated = existingWithPastDeadline.copy(id = "other-id"),
                todayEpochDay = todayEpochDay
            )
        }
    }

    @Test
    fun daysRemaining_andIsOverdue_computeExpectedValues() {
        val futureGoal = sampleGoal(targetDateEpochDay = todayEpochDay + 15L)
        assertEquals(15L, futureGoal.daysRemaining(todayEpochDay))
        assertFalse(futureGoal.isOverdue(todayEpochDay))

        val dueTodayGoal = sampleGoal(targetDateEpochDay = todayEpochDay)
        assertEquals(0L, dueTodayGoal.daysRemaining(todayEpochDay))
        assertFalse(dueTodayGoal.isOverdue(todayEpochDay))

        val overdueGoal = sampleGoal(targetDateEpochDay = todayEpochDay - 5L)
        assertEquals(-5L, overdueGoal.daysRemaining(todayEpochDay))
        assertTrue(overdueGoal.isOverdue(todayEpochDay))
    }

    @Test
    fun parseAmountMinor_rejectsSignsInvalidDecimalsAndOverflow() {
        assertEquals(123_45L, GoalPolicy.parseAmountMinor("123.45", "LKR").getOrThrow())
        assertEquals(123_00L, GoalPolicy.parseAmountMinor("123", "LKR").getOrThrow())
        assertEquals(0L, GoalPolicy.parseAmountMinor("0", "LKR").getOrThrow())
        assertEquals(500L, GoalPolicy.parseAmountMinor("500", "JPY").getOrThrow())
        assertEquals(1_250L, GoalPolicy.parseAmountMinor("1.25", "KWD").getOrThrow())

        assertTrue(GoalPolicy.parseAmountMinor("", "LKR").isFailure)
        assertTrue(GoalPolicy.parseAmountMinor("   ", "LKR").isFailure)
        assertTrue(GoalPolicy.parseAmountMinor("+100", "LKR").isFailure)
        assertTrue(GoalPolicy.parseAmountMinor("-100", "LKR").isFailure)
        assertTrue(GoalPolicy.parseAmountMinor("12.345", "LKR").isFailure)
        assertTrue(GoalPolicy.parseAmountMinor("12.3", "JPY").isFailure)
        assertTrue(GoalPolicy.parseAmountMinor("abc", "LKR").isFailure)
        assertTrue(GoalPolicy.parseAmountMinor("99999999999999999999999", "LKR").isFailure)
    }

    @Test
    fun validateStoredOrRemote_enforcesSchemaInvariants() {
        GoalPolicy.validateStoredOrRemote(sampleGoal())

        assertGoalError(GoalError.INVALID_ID) {
            GoalPolicy.validateStoredOrRemote(sampleGoal(id = "bad/id"))
        }
        assertGoalError(GoalError.INVALID_DATE) {
            GoalPolicy.validateStoredOrRemote(sampleGoal(createdAt = 2_000L, updatedAt = 1_000L))
        }
    }

    @Test
    fun remainingMonths_computesSameDayExactMonthsLeftoverDaysAndFarFuture() {
        val baseDate = LocalDate.of(2026, 3, 15)
        val baseEpoch = baseDate.toEpochDay()

        assertEquals(1L, GoalPolicy.remainingMonths(baseEpoch, baseEpoch))
        assertEquals(1L, sampleGoal(targetDateEpochDay = baseEpoch).remainingMonths(baseEpoch))

        val exactSixMonths = baseDate.plusMonths(6).toEpochDay()
        assertEquals(6L, GoalPolicy.remainingMonths(exactSixMonths, baseEpoch))

        val sixMonthsPlusOneDay = baseDate.plusMonths(6).plusDays(1).toEpochDay()
        assertEquals(7L, GoalPolicy.remainingMonths(sixMonthsPlusOneDay, baseEpoch))

        val farFuture = baseDate.plusYears(10).toEpochDay()
        assertEquals(120L, GoalPolicy.remainingMonths(farFuture, baseEpoch))
    }

    @Test
    fun isActive_checksTodayTomorrowAndYesterday() {
        assertTrue(GoalPolicy.isActive(todayEpochDay, todayEpochDay))
        assertTrue(sampleGoal(targetDateEpochDay = todayEpochDay).isActive(todayEpochDay))

        assertTrue(GoalPolicy.isActive(todayEpochDay + 1L, todayEpochDay))
        assertTrue(sampleGoal(targetDateEpochDay = todayEpochDay + 1L).isActive(todayEpochDay))

        assertFalse(GoalPolicy.isActive(todayEpochDay - 1L, todayEpochDay))
        assertFalse(sampleGoal(targetDateEpochDay = todayEpochDay - 1L).isActive(todayEpochDay))
    }

    @Test
    fun requiredMonthlySavingsMinor_handlesExactCeilingCurrenciesLargeAmountsAndOverdue() {
        val baseDate = LocalDate.of(2026, 1, 10)
        val baseEpoch = baseDate.toEpochDay()
        val threeMonthsEpoch = baseDate.plusMonths(3).toEpochDay()
        val twelveMonthsEpoch = baseDate.plusMonths(12).toEpochDay()

        val exactGoal = sampleGoal(
            targetMinor = 300_000L,
            currency = "LKR",
            targetDateEpochDay = threeMonthsEpoch
        )
        assertEquals(100_000L, exactGoal.requiredMonthlySavingsMinor(baseEpoch))

        val ceilingGoal = sampleGoal(
            targetMinor = 50_000_000L,
            currency = "LKR",
            targetDateEpochDay = twelveMonthsEpoch
        )
        assertEquals(4_166_667L, ceilingGoal.requiredMonthlySavingsMinor(baseEpoch))

        val jpyGoal = sampleGoal(
            targetMinor = 10_000L,
            currency = "JPY",
            targetDateEpochDay = threeMonthsEpoch
        )
        assertEquals(3_334L, jpyGoal.requiredMonthlySavingsMinor(baseEpoch))
        assertEquals("JPY 3334 / month", jpyGoal.toCardUiModel(baseEpoch).monthlyRateFormatted)

        val kwdGoal = sampleGoal(
            targetMinor = 10_000L,
            currency = "KWD",
            targetDateEpochDay = threeMonthsEpoch
        )
        assertEquals(3_334L, kwdGoal.requiredMonthlySavingsMinor(baseEpoch))
        assertEquals("KWD 3.334 / month", kwdGoal.toCardUiModel(baseEpoch).monthlyRateFormatted)

        val nearMaxGoal = sampleGoal(
            targetMinor = Long.MAX_VALUE - 1L,
            currency = "LKR",
            targetDateEpochDay = baseDate.plusMonths(2).toEpochDay()
        )
        assertEquals(4_611_686_018_427_387_903L, nearMaxGoal.requiredMonthlySavingsMinor(baseEpoch))

        val overdueGoal = sampleGoal(
            targetMinor = 100_000L,
            currency = "LKR",
            targetDateEpochDay = baseEpoch - 1L
        )
        assertNull(overdueGoal.requiredMonthlySavingsMinor(baseEpoch))
        assertNull(overdueGoal.toCardUiModel(baseEpoch).monthlyRateFormatted)
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
                MonthlyRateTotalUiModel(currency = "USD", totalFormatted = "USD 250.00 / month"),
                MonthlyRateTotalUiModel(currency = "JPY", totalFormatted = "JPY 2500 / month")
            ),
            calculateMonthlyRateTotals(mixedAndOverdueGoals, baseEpoch)
        )
    }
}
