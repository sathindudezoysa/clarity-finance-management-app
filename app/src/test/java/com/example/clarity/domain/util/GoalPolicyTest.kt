package com.example.clarity.domain.util

import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalError
import com.example.clarity.domain.model.GoalException
import com.example.clarity.domain.model.GoalProgressOperation
import com.example.clarity.domain.model.GoalProgressOperationState
import com.example.clarity.domain.model.GoalProgressOperationType
import com.example.clarity.domain.model.GoalStatus
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
        name: String = "Emergency Fund",
        description: String = "Six months of expenses",
        targetMinor: Long = 100_000L,
        savedMinor: Long = 25_000L,
        currency: String = "LKR",
        targetDateEpochDay: Long = futureEpochDay,
        status: GoalStatus = GoalPolicy.deriveWriteStatus(savedMinor, targetMinor)
    ) = FinancialGoal(
        id = id,
        userId = "user-1",
        name = name,
        description = description,
        targetMinor = targetMinor,
        savedMinor = savedMinor,
        currency = currency,
        targetDateEpochDay = targetDateEpochDay,
        status = status,
        createdAt = 1_000L,
        updatedAt = 1_000L,
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
    fun validateForCreate_acceptsValidGoalAndDerivesStatus() {
        val active = GoalPolicy.validateForCreate(
            sampleGoal(name = "  New Laptop  ", description = " Work machine ", targetMinor = 250_000L, savedMinor = 0L),
            todayEpochDay
        )
        assertEquals("New Laptop", active.name)
        assertEquals("Work machine", active.description)
        assertEquals(GoalStatus.ACTIVE, active.status)

        val completed = GoalPolicy.validateForCreate(
            sampleGoal(targetMinor = 250_000L, savedMinor = 250_000L),
            todayEpochDay
        )
        assertEquals(GoalStatus.COMPLETED, completed.status)
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
    fun validateForCreate_rejectsInvalidTargetSavedAndDates() {
        assertGoalError(GoalError.TARGET_NON_POSITIVE) {
            GoalPolicy.validateForCreate(sampleGoal(targetMinor = 0L, savedMinor = 0L), todayEpochDay)
        }
        assertGoalError(GoalError.TARGET_NON_POSITIVE) {
            GoalPolicy.validateForCreate(sampleGoal(targetMinor = -500L, savedMinor = 0L), todayEpochDay)
        }
        assertGoalError(GoalError.SAVED_NEGATIVE) {
            GoalPolicy.validateForCreate(sampleGoal(targetMinor = 10_000L, savedMinor = -1L), todayEpochDay)
        }
        assertGoalError(GoalError.SAVED_EXCEEDS_TARGET) {
            GoalPolicy.validateForCreate(sampleGoal(targetMinor = 10_000L, savedMinor = 10_001L), todayEpochDay)
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
    fun validateForEdit_allowsUnchangedPastDeadlineAndRejectsTargetBelowSavedOrNewPastDeadline() {
        val existingWithPastDeadline = sampleGoal(
            targetMinor = 100_000L,
            savedMinor = 40_000L,
            targetDateEpochDay = pastEpochDay
        )
        // Unchanged past deadline is allowed on edit
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
        assertEquals(80_000L, edited.targetMinor)
        assertEquals(40_000L, edited.savedMinor)

        // Changing deadline to another past/today date is rejected
        assertGoalError(GoalError.DATE_NOT_IN_FUTURE) {
            GoalPolicy.validateForEdit(
                existing = existingWithPastDeadline,
                updated = existingWithPastDeadline.copy(targetDateEpochDay = todayEpochDay),
                todayEpochDay = todayEpochDay
            )
        }

        // Lowering target below already saved amount is rejected with TARGET_BELOW_SAVED
        assertGoalError(GoalError.TARGET_BELOW_SAVED) {
            GoalPolicy.validateForEdit(
                existing = existingWithPastDeadline,
                updated = existingWithPastDeadline.copy(targetMinor = 39_999L),
                todayEpochDay = todayEpochDay
            )
        }

        // Lowering target to exact saved amount transitions goal to COMPLETED
        val completedByTargetEdit = GoalPolicy.validateForEdit(
            existing = existingWithPastDeadline,
            updated = existingWithPastDeadline.copy(targetMinor = 40_000L),
            todayEpochDay = todayEpochDay
        )
        assertEquals(GoalStatus.COMPLETED, completedByTargetEdit.status)
    }

    @Test
    fun applyOperation_handlesAddAndSetAndStatusTransitionsAndOverflow() {
        val goal = sampleGoal(targetMinor = 100_000L, savedMinor = 25_000L)

        // Positive contribution
        val afterAdd = GoalPolicy.applyOperation(goal, GoalProgressOperationType.ADD, 25_000L)
        assertEquals(50_000L, afterAdd.savedMinor)
        assertEquals(GoalStatus.ACTIVE, afterAdd.status)

        // Contribution reaching exact target transitions to COMPLETED
        val completed = GoalPolicy.applyOperation(afterAdd, GoalProgressOperationType.ADD, 50_000L)
        assertEquals(100_000L, completed.savedMinor)
        assertEquals(GoalStatus.COMPLETED, completed.status)

        // Zero or negative contribution rejected
        assertGoalError(GoalError.CONTRIBUTION_NON_POSITIVE) {
            GoalPolicy.applyOperation(goal, GoalProgressOperationType.ADD, 0L)
        }
        assertGoalError(GoalError.CONTRIBUTION_NON_POSITIVE) {
            GoalPolicy.applyOperation(goal, GoalProgressOperationType.ADD, -100L)
        }

        // Contribution exceeding target rejected
        assertGoalError(GoalError.EXCEEDS_TARGET) {
            GoalPolicy.applyOperation(goal, GoalProgressOperationType.ADD, 75_001L)
        }

        // Overflow protection on ADD near Long.MAX_VALUE
        val hugeGoal = sampleGoal(targetMinor = Long.MAX_VALUE, savedMinor = Long.MAX_VALUE - 10L)
        assertGoalError(GoalError.EXCEEDS_TARGET) {
            GoalPolicy.applyOperation(hugeGoal, GoalProgressOperationType.ADD, 20L)
        }

        // Set balance at 0, mid, and target
        val setZero = GoalPolicy.applyOperation(completed, GoalProgressOperationType.SET, 0L)
        assertEquals(0L, setZero.savedMinor)
        assertEquals(GoalStatus.ACTIVE, setZero.status) // Reactivated when corrected downward

        val setMid = GoalPolicy.applyOperation(setZero, GoalProgressOperationType.SET, 60_000L)
        assertEquals(60_000L, setMid.savedMinor)
        assertEquals(GoalStatus.ACTIVE, setMid.status)

        val setTarget = GoalPolicy.applyOperation(setMid, GoalProgressOperationType.SET, 100_000L)
        assertEquals(100_000L, setTarget.savedMinor)
        assertEquals(GoalStatus.COMPLETED, setTarget.status)

        // Set balance < 0 or > target rejected
        assertGoalError(GoalError.SAVED_NEGATIVE) {
            GoalPolicy.applyOperation(goal, GoalProgressOperationType.SET, -1L)
        }
        assertGoalError(GoalError.EXCEEDS_TARGET) {
            GoalPolicy.applyOperation(goal, GoalProgressOperationType.SET, 100_001L)
        }
    }

    @Test
    fun progressAndRemainingCalculations_floorPercentSo99Point9NeverShows100() {
        assertEquals(0, GoalPolicy.progressPercent(0L, 1_000L))
        assertEquals(0f, GoalPolicy.progressFraction(0L, 1_000L), 0.0001f)
        assertEquals(1_000L, GoalPolicy.remainingMinor(targetMinor = 1_000L, savedMinor = 0L))

        assertEquals(50, GoalPolicy.progressPercent(500L, 1_000L))
        assertEquals(0.5f, GoalPolicy.progressFraction(500L, 1_000L), 0.0001f)
        assertEquals(500L, GoalPolicy.remainingMinor(targetMinor = 1_000L, savedMinor = 500L))

        // 999 / 1000 = 99.9% -> floor to 99%, never 100%
        assertEquals(99, GoalPolicy.progressPercent(999L, 1_000L))
        assertTrue(GoalPolicy.progressFraction(999L, 1_000L) < 1f)
        assertEquals(1L, GoalPolicy.remainingMinor(targetMinor = 1_000L, savedMinor = 999L))

        // 1000 / 1000 = 100%
        assertEquals(100, GoalPolicy.progressPercent(1_000L, 1_000L))
        assertEquals(1f, GoalPolicy.progressFraction(1_000L, 1_000L), 0.0001f)
        assertEquals(0L, GoalPolicy.remainingMinor(targetMinor = 1_000L, savedMinor = 1_000L))

        // Overdue check: past deadline is overdue only when not COMPLETED
        val overdueActive = sampleGoal(savedMinor = 50_000L, targetDateEpochDay = pastEpochDay, status = GoalStatus.ACTIVE)
        val pastCompleted = sampleGoal(savedMinor = 100_000L, targetDateEpochDay = pastEpochDay, status = GoalStatus.COMPLETED)
        assertTrue(GoalPolicy.isOverdue(overdueActive, todayEpochDay))
        assertFalse(GoalPolicy.isOverdue(pastCompleted, todayEpochDay))
    }

    @Test
    fun parseAmountMinor_rejectsSignsInvalidDecimalsAndOverflow() {
        assertEquals(123_45L, GoalPolicy.parseAmountMinor("123.45", "LKR").getOrThrow())
        assertEquals(123_00L, GoalPolicy.parseAmountMinor("123", "LKR").getOrThrow())
        assertEquals(0L, GoalPolicy.parseAmountMinor("0", "LKR").getOrThrow())
        assertEquals(500L, GoalPolicy.parseAmountMinor("500", "JPY").getOrThrow())
        assertEquals(1_250L, GoalPolicy.parseAmountMinor("1.25", "KWD").getOrThrow())

        // Reject signs, blanks, non-digits, too many decimals, overflow
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
    fun replayPendingOperations_replaysInOrderAndDistinctlyHandlesAddVersusSet() {
        val ops = listOf(
            GoalProgressOperation(
                id = "op-1",
                userId = "user-1",
                goalId = "goal-1",
                type = GoalProgressOperationType.ADD,
                amountMinor = 15_000L,
                createdAt = 1_100L,
                state = GoalProgressOperationState.PENDING
            ),
            GoalProgressOperation(
                id = "op-2",
                userId = "user-1",
                goalId = "goal-1",
                type = GoalProgressOperationType.SET,
                amountMinor = 10_000L,
                createdAt = 1_200L,
                state = GoalProgressOperationState.PENDING
            ),
            GoalProgressOperation(
                id = "op-3",
                userId = "user-1",
                goalId = "goal-1",
                type = GoalProgressOperationType.ADD,
                amountMinor = 90_000L,
                createdAt = 1_300L,
                state = GoalProgressOperationState.PENDING
            ),
            GoalProgressOperation(
                id = "op-applied",
                userId = "user-1",
                goalId = "goal-1",
                type = GoalProgressOperationType.ADD,
                amountMinor = 50_000L,
                createdAt = 1_050L,
                state = GoalProgressOperationState.APPLIED
            )
        )
        val replayed = GoalPolicy.replayPendingOperations(
            baseSavedMinor = 20_000L,
            targetMinor = 100_000L,
            pendingOperations = ops
        )
        assertEquals(100_000L, replayed)
    }
}

