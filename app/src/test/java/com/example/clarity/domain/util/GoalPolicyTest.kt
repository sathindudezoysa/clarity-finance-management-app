package com.example.clarity.domain.util

import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalError
import com.example.clarity.domain.model.GoalException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
